package com.example.healthserviceapp.service;

import java.util.List;
import java.util.ArrayList;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import com.example.healthserviceapp.entity.Disponibilidad;
import com.example.healthserviceapp.utility.Dias;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.healthserviceapp.entity.Consulta;
import com.example.healthserviceapp.entity.Paciente;
import com.example.healthserviceapp.entity.Profesional;
import com.example.healthserviceapp.entity.Usuario;
import com.example.healthserviceapp.enums.Rol;
import com.example.healthserviceapp.enums.EstadoConsulta;
import com.example.healthserviceapp.repository.ConsultaRepository;
import com.example.healthserviceapp.repository.PacienteRepository;
import com.example.healthserviceapp.repository.ProfesionalRepository;
import com.example.healthserviceapp.repository.UsuarioRepository;

@Service
public class ConsultaService {
    @Autowired
    private Clock reservaClock;
    @Autowired
    private ConsultaRepository consultaRepository;

    @Autowired
    private ProfesionalRepository profesionalRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<Consulta> listarHistorial(String id) {
        Usuario profesional = usuarioActual(Rol.PROFESIONAL);
        List<Consulta> historial = consultaRepository.findByPacienteIdAndProfesionalIdOrderByFechaAscHorarioAsc(
                id, profesional.getId());
        if (historial.isEmpty()) {
            throw accesoDenegado();
        }
        return historial;
    }

    @Transactional
    public void ingresarDiagnostico(String id, String diagnostico, String idPaciente) {
        Usuario profesional = usuarioActual(Rol.PROFESIONAL);
        Consulta consulta = consultaRepository.findByIdAndProfesionalId(id, profesional.getId())
                .orElseThrow(this::accesoDenegado);
        // Ambos identificadores del formulario deben referirse a la misma consulta.
        if (!consulta.getPaciente().getId().equals(idPaciente)) {
            throw accesoDenegado();
        }
        if (!puedeDiagnosticar(consulta)) {
            throw new EstadoConsultaException("Solo se puede finalizar una consulta reservada después de su hora de atención.");
        }
        if (diagnostico == null || diagnostico.trim().isEmpty() || diagnostico.length() > 1500) {
            throw new EstadoConsultaException("Ingresá un diagnóstico de entre 1 y 1500 caracteres.");
        }
        consulta.setDiagnostico(diagnostico.trim());
        consulta.setEstado(EstadoConsulta.FINALIZADA);
        consulta.setFinalizadaEn(LocalDateTime.now(reservaClock));
        consultaRepository.saveAndFlush(consulta);
    }

    @Transactional(readOnly = true)
    public List<Paciente> listarPacientes(String id) {
        verificarIdentidad(id, Rol.PROFESIONAL);
        return consultaRepository.listarPacientesPorProfesional(id);
    }    

    @Transactional(readOnly = true)
    public List<String> listarConsultasPorProfesionalAgrupadoPorFecha(Profesional profesional, Long nTurnos) {
        return consultaRepository.listarConsultasPorProfesionalAgrupadoPorFecha(profesional, nTurnos);
    }

    @Transactional(readOnly = true)
    public List<Integer> listarHorarioPorProfesionalPorFecha(Profesional profesional, String fecha) {
        return consultaRepository.listarHorarioPorProfesionalPorFecha(profesional, fecha);
    }

    @Transactional
    public void guardarConsulta(String idProfesional, String idPaciente, String fecha, Integer horario) {
        verificarIdentidad(idPaciente, Rol.PACIENTE);
        Profesional profesional = profesionalReservable(idProfesional);
        LocalDateTime ahora = LocalDateTime.now(reservaClock);
        LocalDate dia = validarFecha(fecha, ahora.toLocalDate());
        if (horario == null || !horariosLaborales(profesional, dia, ahora).contains(horario)) {
            throw new ReservaInvalidaException("El día u horario elegido no está disponible. Elegí otro turno.");
        }
        if (consultaRepository.existsByProfesionalIdAndFechaAndHorarioAndTurnoActivoTrue(idProfesional, fecha, horario)
                || consultaRepository.existsByPacienteIdAndFechaAndHorarioAndTurnoActivoTrue(idPaciente, fecha, horario)) {
            throw new ReservaInvalidaException("Ese horario ya está ocupado o coincide con otro de tus turnos.");
        }
        Paciente paciente = pacienteRepository.findById(idPaciente).orElseThrow(this::accesoDenegado);
        if (paciente.getObraSocial() == null || paciente.getObraSocial().getPrecio() == null
                || !Double.isFinite(paciente.getObraSocial().getPrecio()) || paciente.getObraSocial().getPrecio() < 0) {
            throw new ReservaInvalidaException("Completá una cobertura válida en tu perfil antes de reservar.");
        }
        Consulta consulta = new Consulta();
        consulta.setProfesional(profesional);
        consulta.setPaciente(paciente);
        consulta.setFecha(dia.toString());
        consulta.setHorario(horario);
        consulta.setPrecio(Math.max(0, profesional.getPrecioConsulta() - paciente.getObraSocial().getPrecio()));
        // La restricción única decide también cuando dos solicitudes superan la comprobación anterior.
        consultaRepository.saveAndFlush(consulta);
    }

    @Transactional(readOnly = true)
    public Profesional profesionalReservable(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ReservaInvalidaException("Seleccioná un profesional disponible.");
        }
        Profesional profesional = profesionalRepository.findById(id)
                .orElseThrow(() -> new ReservaInvalidaException("El profesional elegido no está disponible."));
        if (!Boolean.TRUE.equals(profesional.getActivo()) || profesional.getRol() != Rol.PROFESIONAL
                || profesional.getPrecioConsulta() == null || !Double.isFinite(profesional.getPrecioConsulta())
                || profesional.getPrecioConsulta() < 0 || !agendaValida(profesional.getDisponibilidad())) {
            throw new ReservaInvalidaException("El profesional elegido no tiene una agenda disponible.");
        }
        return profesional;
    }

    @Transactional(readOnly = true)
    public List<String> fechasDisponibles(String idProfesional, String idPaciente) {
        verificarIdentidad(idPaciente, Rol.PACIENTE);
        Profesional profesional = profesionalReservable(idProfesional);
        LocalDateTime ahora = LocalDateTime.now(reservaClock);
        LocalDate hoy = ahora.toLocalDate();
        List<Consulta> ocupados = consultaRepository.turnosOcupados(idProfesional, idPaciente,
                hoy.toString(), hoy.plusDays(90).toString());
        List<String> fechas = new ArrayList<>();
        for (LocalDate dia = hoy; !dia.isAfter(hoy.plusDays(90)); dia = dia.plusDays(1)) {
            if (!quitarOcupados(horariosLaborales(profesional, dia, ahora), ocupados, dia.toString()).isEmpty()) {
                fechas.add(dia.toString());
            }
        }
        return fechas;
    }

    @Transactional(readOnly = true)
    public List<Integer> horariosDisponibles(String idProfesional, String idPaciente, String fecha) {
        verificarIdentidad(idPaciente, Rol.PACIENTE);
        Profesional profesional = profesionalReservable(idProfesional);
        LocalDateTime ahora = LocalDateTime.now(reservaClock);
        LocalDate dia = validarFecha(fecha, ahora.toLocalDate());
        return quitarOcupados(horariosLaborales(profesional, dia, ahora),
                consultaRepository.turnosOcupados(idProfesional, idPaciente, fecha, fecha), fecha);
    }

    private List<Integer> quitarOcupados(List<Integer> horarios, List<Consulta> ocupados, String fecha) {
        for (Consulta consulta : ocupados) {
            if (fecha.equals(consulta.getFecha())) {
                horarios.remove(consulta.getHorario());
            }
        }
        return horarios;
    }

    private LocalDate validarFecha(String fecha, LocalDate hoy) {
        try {
            if (fecha == null || !fecha.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new DateTimeParseException("Formato inválido", "", 0);
            }
            LocalDate dia = LocalDate.parse(fecha);
            if (dia.isBefore(hoy) || dia.isAfter(hoy.plusDays(90))) {
                throw new ReservaInvalidaException("Elegí una fecha desde hoy hasta los próximos 90 días.");
            }
            return dia;
        } catch (DateTimeParseException ex) {
            throw new ReservaInvalidaException("Ingresá una fecha válida con formato AAAA-MM-DD.");
        }
    }

    private boolean agendaValida(Disponibilidad d) {
        return d != null && d.getDias() != null && d.getDias().length > 0
                && d.getEntrada() != null && d.getInicioDescanso() != null
                && d.getFinDescanso() != null && d.getSalida() != null
                && d.getEntrada() >= 0 && d.getSalida() <= 24 && d.getEntrada() < d.getSalida()
                && d.getEntrada() <= d.getInicioDescanso() && d.getInicioDescanso() <= d.getFinDescanso()
                && d.getFinDescanso() <= d.getSalida();
    }

    private List<Integer> horariosLaborales(Profesional profesional, LocalDate dia, LocalDateTime ahora) {
        List<Integer> horarios = new ArrayList<>();
        Disponibilidad d = profesional.getDisponibilidad();
        if (new Dias(d.getDias()).comprobar(dia.getDayOfWeek())) {
            for (int hora = d.getEntrada(); hora < d.getSalida(); hora++) {
                if ((hora < d.getInicioDescanso() || hora >= d.getFinDescanso())
                        && dia.atTime(hora, 0).isAfter(ahora)) {
                    horarios.add(hora);
                }
            }
        }
        return horarios;
    }

    @Transactional(readOnly = true)
    public List<Consulta> listarConsulta(String id) {
        verificarIdentidad(id, Rol.PACIENTE);
        return consultaRepository.listarConsulta(id);
    }

    @Transactional
    public void eliminar(String id) {
        Consulta consulta = consultaDelPaciente(id);
        if (consulta.getEstado() == EstadoConsulta.CANCELADA) { return; }
        if (!puedeCancelar(consulta)) {
            throw new EstadoConsultaException("Solo podés cancelar una consulta reservada antes de su inicio.");
        }
        consulta.setEstado(EstadoConsulta.CANCELADA);
        consulta.setCanceladaEn(LocalDateTime.now(reservaClock));
        consultaRepository.saveAndFlush(consulta);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void guardarCalificacion(String id, Integer calificacion) {
        Consulta consulta = consultaDelPaciente(id);
        if (!puedeCalificar(consulta) || calificacion == null || calificacion < 1 || calificacion > 5) {
            throw new EstadoConsultaException("Solo podés calificar una consulta finalizada, una vez y con un valor de 1 a 5.");
        }
        // Serializa el promedio; READ_COMMITTED ve las notas confirmadas mientras esperábamos el bloqueo.
        Profesional profesional = profesionalRepository.bloquearParaCalificacion(consulta.getProfesional().getId())
                .orElseThrow(this::accesoDenegado);
        consulta.setCalificacion(calificacion);
        consultaRepository.saveAndFlush(consulta);
        profesional.setCalificacion(consultaRepository.promedioCalificacionPorProfesional(profesional.getId()));
        profesionalRepository.saveAndFlush(profesional);
    }

    public boolean puedeCancelar(Consulta consulta) {
        return consulta.getEstado() == EstadoConsulta.RESERVADA
                && inicio(consulta).isAfter(LocalDateTime.now(reservaClock));
    }

    public boolean puedeDiagnosticar(Consulta consulta) {
        return consulta.getEstado() == EstadoConsulta.RESERVADA
                && !inicio(consulta).plusHours(1).isAfter(LocalDateTime.now(reservaClock));
    }

    public boolean puedeCalificar(Consulta consulta) {
        return consulta.getEstado() == EstadoConsulta.FINALIZADA && consulta.getCalificacion() == null
                && !inicio(consulta).plusHours(1).isAfter(LocalDateTime.now(reservaClock));
    }

    private LocalDateTime inicio(Consulta consulta) {
        return LocalDate.parse(consulta.getFecha()).atTime(consulta.getHorario(), 0);
    }

    @Transactional(readOnly = true)
    public Double promedioCalificacionPorProfesional(String id) {
        try {
            return consultaRepository.promedioCalificacionPorProfesional(id);   
        } catch (Exception e) {
            return 0d;
        }
    }

    @Transactional(readOnly = true)
    public Consulta buscarConsulta(String id) {
        return consultaDelPaciente(id);
    }

    private Consulta consultaDelPaciente(String id) {
        Usuario paciente = usuarioActual(Rol.PACIENTE);
        return consultaRepository.findByIdAndPacienteId(id, paciente.getId())
                .orElseThrow(this::accesoDenegado);
    }

    private void verificarIdentidad(String id, Rol rol) {
        if (!usuarioActual(rol).getId().equals(id)) {
            throw accesoDenegado();
        }
    }

    private Usuario usuarioActual(Rol rol) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw accesoDenegado();
        }
        // La identidad proviene de Spring Security, nunca del id recibido o del perfil en sesión.
        Usuario usuario = usuarioRepository.buscarPorEmail(autenticacion.getName());
        if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo()) || usuario.getRol() != rol
                || autenticacion.getAuthorities().stream()
                    .noneMatch(permiso -> permiso.getAuthority().equals("ROLE_" + rol.name()))) {
            throw accesoDenegado();
        }
        return usuario;
    }

    private AccessDeniedException accesoDenegado() {
        // Misma respuesta para recursos inexistentes y ajenos, sin revelar su existencia.
        return new AccessDeniedException("No tenés permisos para acceder a esta consulta.");
    }
}
