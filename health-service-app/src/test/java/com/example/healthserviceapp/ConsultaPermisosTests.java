package com.example.healthserviceapp;

import com.example.healthserviceapp.entity.*;
import com.example.healthserviceapp.enums.Especialidad;
import com.example.healthserviceapp.enums.Rol;
import com.example.healthserviceapp.enums.Sexo;
import com.example.healthserviceapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
class ConsultaPermisosTests {
    @Autowired private MockMvc mvc;
    @Autowired private PacienteRepository pacientes;
    @Autowired private ProfesionalRepository profesionales;
    @Autowired private ConsultaRepository consultas;
    @Autowired private EntityManager entityManager;

    private Paciente paciente1;
    private Paciente paciente2;
    private Profesional profesional1;
    private Profesional profesional2;
    private Consulta consulta1;
    private Consulta compartida;
    private Consulta consulta2;

    @BeforeEach
    void prepararDatos() {
        paciente1 = paciente("paciente1@etapa1.test", "PacienteUno");
        paciente2 = paciente("paciente2@etapa1.test", "PacienteDos");
        profesional1 = profesional("profesional1@etapa1.test", "ProfesionalUno");
        profesional2 = profesional("profesional2@etapa1.test", "ProfesionalDos");
        consulta1 = consulta(paciente1, profesional1, "Observacion exclusiva uno", 9);
        compartida = consulta(paciente1, profesional2, "Observacion exclusiva dos", 10);
        consulta2 = consulta(paciente2, profesional2, "Observacion paciente dos", 11);
        entityManager.flush();
    }

    @Test
    void profesionalSoloVeSusConsultasInclusoConPacienteCompartido() throws Exception {
        ResultActions resultado = actuar(profesional1, get("/consulta/consultas")
                .param("idPaciente", paciente1.getId()));
        resultado.andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Observacion exclusiva uno")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Observacion exclusiva dos"))));
        assertThat(historial(resultado)).extracting(Consulta::getId).containsExactly(consulta1.getId());
    }

    @Test
    void segundoProfesionalVeSoloSusConsultasDelPacienteCompartido() throws Exception {
        ResultActions resultado = actuar(profesional2, get("/consulta/consultas")
                .param("idPaciente", paciente1.getId())).andExpect(status().isOk());
        assertThat(historial(resultado)).extracting(Consulta::getId).containsExactly(compartida.getId());
    }

    @Test
    void profesionalNoPuedeAbrirPacienteSinRelacion() throws Exception {
        actuar(profesional1, get("/consulta/consultas").param("idPaciente", paciente2.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void profesionalNoPuedeModificarConsultaDeOtroAunqueCompartanPaciente() throws Exception {
        actuar(profesional1, diagnostico(compartida, paciente1, "Cambio ajeno"))
                .andExpect(status().isForbidden());
        assertThat(recargar(compartida).getDiagnostico()).isEqualTo("Observacion exclusiva dos");
    }

    @Test
    void diagnosticoRechazaPacienteManipuladoAntesDeGuardar() throws Exception {
        actuar(profesional1, diagnostico(consulta1, paciente2, "Cambio inconsistente"))
                .andExpect(status().isForbidden());
        assertThat(recargar(consulta1).getDiagnostico()).isEqualTo("Observacion exclusiva uno");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void profesionalPuedeGuardarDiagnosticoPropio(int numero) throws Exception {
        Profesional actor = numero == 1 ? profesional1 : profesional2;
        Consulta propia = numero == 1 ? consulta1 : consulta2;
        Paciente paciente = numero == 1 ? paciente1 : paciente2;
        actuar(actor, diagnostico(propia, paciente, "Observacion autorizada"))
                .andExpect(status().isOk());
        assertThat(recargar(propia).getDiagnostico()).isEqualTo("Observacion autorizada");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void pacienteNoPuedeCancelarConsultaAjena(int numero) throws Exception {
        Paciente actor = numero == 1 ? paciente1 : paciente2;
        Consulta ajena = numero == 1 ? consulta2 : consulta1;
        actuar(actor, get("/consulta/eliminar/{id}", ajena.getId())).andExpect(status().isForbidden());
        assertThat(recargar(ajena)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void pacienteNoPuedeCalificarConsultaAjenaNiCambiarPromedio(int numero) throws Exception {
        Paciente actor = numero == 1 ? paciente1 : paciente2;
        Consulta ajena = numero == 1 ? consulta2 : consulta1;
        String idProfesional = ajena.getProfesional().getId();
        actuar(actor, post("/consulta/calificar/{id}", ajena.getId()).param("calificacion", "1"))
                .andExpect(status().isForbidden());
        assertThat(recargar(ajena).getCalificacion()).isNull();
        assertThat(profesionales.findById(idProfesional).get().getCalificacion()).isEqualTo(4.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void pacientePuedeCancelarConsultaPropia(int numero) throws Exception {
        Paciente actor = numero == 1 ? paciente1 : paciente2;
        Consulta propia = numero == 1 ? consulta1 : consulta2;
        actuar(actor, get("/consulta/eliminar/{id}", propia.getId())).andExpect(status().isOk());
        assertThat(recargar(propia)).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void pacientePuedeCalificarConsultaPropia(int numero) throws Exception {
        Paciente actor = numero == 1 ? paciente1 : paciente2;
        Consulta propia = numero == 1 ? consulta1 : consulta2;
        actuar(actor, post("/consulta/calificar/{id}", propia.getId()).param("calificacion", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/paciente/consulta"));
        assertThat(recargar(propia).getCalificacion()).isEqualTo(5);
    }

    @Test
    void pacienteSoloVeSuListado() throws Exception {
        ResultActions resultado = actuar(paciente1, get("/paciente/consulta")).andExpect(status().isOk());
        assertThat(historial(resultado)).extracting(Consulta::getId)
                .containsExactlyInAnyOrder(consulta1.getId(), compartida.getId());
    }

    @Test
    void identidadAutenticadaPrevaleceSobreAtributoDeSesion() throws Exception {
        mvc.perform(get("/paciente/consulta")
                        .with(user(paciente1.getEmail()).roles("PACIENTE"))
                        .sessionAttr("usuariosession", paciente2))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteInactivoNoPuedeModificarConSesionAnterior() throws Exception {
        paciente1.setActivo(false);
        pacientes.saveAndFlush(paciente1);
        actuar(paciente1, post("/consulta/calificar/{id}", consulta1.getId()).param("calificacion", "1"))
                .andExpect(status().isForbidden());
        assertThat(recargar(consulta1).getCalificacion()).isNull();
    }

    @Test
    void identificadoresInexistentesNoRevelanDatosNiProducenErrorInterno() throws Exception {
        actuar(paciente1, get("/consulta/eliminar/no-existe")).andExpect(status().isForbidden());
        actuar(paciente1, post("/consulta/calificar/no-existe").param("calificacion", "5"))
                .andExpect(status().isForbidden());
        actuar(profesional1, get("/consulta/consultas").param("idPaciente", "no-existe"))
                .andExpect(status().isForbidden());
        actuar(profesional1, get("/consulta/diagnostico").param("id", "no-existe")
                .param("idPaciente", paciente1.getId()).param("diagnostico", "Prueba"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesIncorrectosNoPuedenUsarOperacionesClinicas() throws Exception {
        actuar(paciente1, get("/consulta/consultas").param("idPaciente", paciente1.getId()))
                .andExpect(status().isForbidden());
        actuar(paciente1, diagnostico(consulta1, paciente1, "Prueba")).andExpect(status().isForbidden());
        actuar(profesional1, get("/consulta/eliminar/{id}", consulta1.getId()))
                .andExpect(status().isForbidden());
        actuar(profesional1, post("/consulta/calificar/{id}", consulta1.getId()).param("calificacion", "5"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/consulta/consultas").param("idPaciente", paciente1.getId())
                .with(user("admin@admin.com").roles("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void sinSesionSeSolicitaIngreso() throws Exception {
        mvc.perform(get("/consulta/consultas").param("idPaciente", paciente1.getId()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/consulta/eliminar/{id}", consulta1.getId()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/consulta/calificar/{id}", consulta1.getId()).param("calificacion", "5"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(diagnostico(consulta1, paciente1, "Prueba")).andExpect(status().is3xxRedirection());
        assertThat(recargar(consulta1).getDiagnostico()).isEqualTo("Observacion exclusiva uno");
        assertThat(recargar(consulta1).getCalificacion()).isNull();
    }

    private ResultActions actuar(Usuario actor, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(user(actor.getEmail()).roles(actor.getRol().name()))
                .sessionAttr("usuariosession", actor));
    }

    @SuppressWarnings("unchecked")
    private List<Consulta> historial(ResultActions resultado) {
        return (List<Consulta>) resultado.andReturn().getModelAndView().getModel().get("consulta");
    }

    private MockHttpServletRequestBuilder diagnostico(Consulta consulta, Paciente paciente, String texto) {
        return get("/consulta/diagnostico").param("id", consulta.getId())
                .param("idPaciente", paciente.getId()).param("diagnostico", texto);
    }

    private Consulta recargar(Consulta consulta) {
        String id = consulta.getId();
        entityManager.flush();
        entityManager.clear();
        return consultas.findById(id).orElse(null);
    }

    private Paciente paciente(String email, String nombre) {
        Paciente paciente = new Paciente();
        datosPersona(paciente, email, nombre, Rol.PACIENTE);
        return pacientes.save(paciente);
    }

    private Profesional profesional(String email, String nombre) {
        Profesional profesional = new Profesional();
        datosPersona(profesional, email, nombre, Rol.PROFESIONAL);
        profesional.setEspecialidad(Especialidad.CARDIOLOGIA);
        profesional.setCalificacion(4.0);
        return profesionales.save(profesional);
    }

    private void datosPersona(Persona persona, String email, String nombre, Rol rol) {
        persona.setEmail(email);
        persona.setNombre(nombre);
        persona.setApellido("Prueba");
        persona.setDomicilio("Domicilio ficticio");
        persona.setDni(12345678);
        persona.setFechaNacimiento(java.sql.Date.valueOf("1990-01-01"));
        persona.setSexo(Sexo.FEMENINO);
        persona.setRol(rol);
        persona.setActivo(true);
    }

    private Consulta consulta(Paciente paciente, Profesional profesional, String diagnostico, int hora) {
        Consulta consulta = new Consulta();
        consulta.setPaciente(paciente);
        consulta.setProfesional(profesional);
        consulta.setFecha(LocalDate.now().minusDays(1).toString());
        consulta.setHorario(hora);
        consulta.setPrecio(1000.0);
        consulta.setDiagnostico(diagnostico);
        return consultas.save(consulta);
    }
}
