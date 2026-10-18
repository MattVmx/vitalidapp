package com.example.healthserviceapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.healthserviceapp.entity.Consulta;
import com.example.healthserviceapp.entity.Paciente;
import com.example.healthserviceapp.entity.Profesional;
import com.example.healthserviceapp.entity.Usuario;
import com.example.healthserviceapp.enums.Rol;
import com.example.healthserviceapp.repository.ConsultaRepository;
import com.example.healthserviceapp.repository.PacienteRepository;
import com.example.healthserviceapp.repository.ProfesionalRepository;
import com.example.healthserviceapp.repository.UsuarioRepository;

@Service
public class ConsultaService {
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
        consulta.setDiagnostico(diagnostico);
        consultaRepository.save(consulta);
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
        Optional<Profesional> respuestaProfesional = profesionalRepository.findById(idProfesional);
        Optional<Paciente> respuestaPaciente = pacienteRepository.findById(idPaciente);
        if (respuestaProfesional.isPresent() && respuestaPaciente.isPresent()) {
            Profesional profesional = respuestaProfesional.get();
            Paciente paciente = respuestaPaciente.get();
            Consulta consulta = new Consulta();
            consulta.setProfesional(profesional);
            consulta.setPaciente(paciente);
            consulta.setFecha(fecha);
            consulta.setHorario(horario);
            consulta.setDiagnostico(null);
            Double precio = profesional.getPrecioConsulta() - paciente.getObraSocial().getPrecio();
            consulta.setPrecio(precio);
            consultaRepository.save(consulta);
        }
    }

    @Transactional(readOnly = true)
    public List<Consulta> listarConsulta(String id) {
        verificarIdentidad(id, Rol.PACIENTE);
        return consultaRepository.listarConsulta(id);
    }

    @Transactional
    public void eliminar(String id) {
        Consulta consulta = consultaDelPaciente(id);
        consultaRepository.delete(consulta);
    }

    @Transactional
    public void guardarCalificacion(String id, Integer calificacion) {
        Consulta consulta = consultaDelPaciente(id);
        consulta.setCalificacion(calificacion);
        consultaRepository.save(consulta);
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
