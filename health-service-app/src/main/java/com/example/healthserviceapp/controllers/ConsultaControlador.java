
package com.example.healthserviceapp.controllers;


import javax.servlet.http.HttpSession;

import com.example.healthserviceapp.entity.Consulta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.healthserviceapp.entity.Paciente;
import com.example.healthserviceapp.entity.Profesional;
import com.example.healthserviceapp.service.ConsultaService;
import com.example.healthserviceapp.service.PacienteService;
import com.example.healthserviceapp.service.ProfesionalService;
import com.example.healthserviceapp.service.ReservaInvalidaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/consulta")
public class ConsultaControlador {
    @Autowired
    private ProfesionalService profesionalService;

    @Autowired
    private ConsultaService consultaService;
    
    @Autowired
    private PacienteService pacienteService;

    @PreAuthorize("hasAnyRole('ROLE_PROFESIONAL')")
    @GetMapping("/paciente")
    public String pacientes(HttpSession session, ModelMap modelo) {
        Profesional profesional = (Profesional) session.getAttribute("usuariosession");
        modelo.addAttribute("pacientes", consultaService.listarPacientes(profesional.getId()));
        return "pacientes_paso1.html";
    }

    @PreAuthorize("hasAnyRole('ROLE_PROFESIONAL')")
    @GetMapping("/consultas")    
    public String listarConsultasPaciente(ModelMap modelo, @RequestParam String idPaciente){
        modelo.addAttribute("consulta", consultaService.listarHistorial(idPaciente));
        modelo.addAttribute("paciente", pacienteService.listarUnPaciente(idPaciente));
        return "pacientes_paso2.html";
    }

    @PreAuthorize("hasAnyRole('ROLE_PROFESIONAL')")
    @PostMapping("/diagnostico")
    public String darDiagnostico(@RequestParam String id, @RequestParam String diagnostico,
            @RequestParam String idPaciente, RedirectAttributes redirect) {
        consultaService.ingresarDiagnostico(id, diagnostico, idPaciente);
        redirect.addAttribute("idPaciente", idPaciente);
        return "redirect:/consulta/consultas";
    }

    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @GetMapping("/provincia")
    public String provincias(HttpSession session, ModelMap modelo) {
        modelo.put("provincias", profesionalService.listarProvincias());
        return "consulta_paso1.html";
    }

    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @GetMapping("/especialidad")
    public String especialidades(@RequestParam String provincia, HttpSession session, ModelMap modelo) {
        modelo.put("provincia", provincia);
        modelo.put("especialidades", profesionalService.listarEspecialidadesPorProvincia(provincia));
        return "consulta_paso2.html";
    }

    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @GetMapping("/profesional")
    public String profesionales(@RequestParam String provincia, @RequestParam String especialidad, HttpSession session,
            ModelMap modelo) {
        modelo.put("profesionales",
                profesionalService.listarProfesionalPorEspecialidadesPorProvincia(provincia, especialidad));
        return "consulta_paso3.html";
    }

    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @GetMapping("/disponibilidad")
    public String disponibilidadProfesional(@RequestParam String idProfesional, HttpSession session, ModelMap modelo) {
        Profesional profesional = consultaService.profesionalReservable(idProfesional);
        modelo.put("profesional", profesional);
        Paciente paciente = (Paciente) session.getAttribute("usuariosession");
        modelo.put("fechas", consultaService.fechasDisponibles(idProfesional, paciente.getId()));
        return "consulta_paso4.html";
    }

    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @GetMapping("/horario")
    public String horarioProfesional(@RequestParam String idProfesional, @RequestParam String fecha,
            HttpSession session, ModelMap modelo) {
        Profesional profesional = consultaService.profesionalReservable(idProfesional);
        modelo.put("profesional", profesional);
        modelo.put("fecha", fecha);
        Paciente paciente = (Paciente) session.getAttribute("usuariosession");
        modelo.put("horarios", consultaService.horariosDisponibles(idProfesional, paciente.getId(), fecha));
        return "consulta_paso5.html";
    }

    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @PostMapping("/reservar")
    public String reservar(@RequestParam String idProfesional, @RequestParam String fecha,
            @RequestParam Integer horario, HttpSession session, RedirectAttributes redirect) {
        Paciente paciente = (Paciente) session.getAttribute("usuariosession");
        try {
            consultaService.guardarConsulta(idProfesional, paciente.getId(), fecha, horario);
        } catch (DataIntegrityViolationException ex) {
            // Se captura fuera de la transacción: el intento fallido ya se revirtió.
            redirect.addFlashAttribute("error", "No se pudo reservar: el horario dejó de estar disponible. Elegí otro turno.");
            return "redirect:/consulta/provincia";
        }
        redirect.addFlashAttribute("exito", "Tu turno quedó confirmado. Podés verlo en Mis consultas.");
        return "redirect:/paciente/consulta";
    }

    @ExceptionHandler(ReservaInvalidaException.class)
    public String reservaInvalida(ReservaInvalidaException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", ex.getMessage());
        return "redirect:/consulta/provincia";
    }

    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @PostMapping("/eliminar/{id}")
    public String eliminarConsulta(@PathVariable String id) {
        consultaService.eliminar(id);
        return "redirect:/paciente/consulta";
    }
    
    @PreAuthorize("hasAnyRole('ROLE_PACIENTE')")
    @PostMapping("/calificar/{id}")
    public String guardarCalificacion(@PathVariable String id, @RequestParam int calificacion, ModelMap modelo){
        consultaService.guardarCalificacion(id, calificacion);
        Consulta consulta = consultaService.buscarConsulta(id);
        String idProfesional = consulta.getProfesional().getId();
        Double promedio = consultaService.promedioCalificacionPorProfesional(idProfesional);
        profesionalService.guardarCalificacion(idProfesional, promedio);
        return "redirect:/paciente/consulta";
    }
}
