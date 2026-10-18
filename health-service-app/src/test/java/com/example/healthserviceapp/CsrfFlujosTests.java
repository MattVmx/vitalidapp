package com.example.healthserviceapp;

import com.example.healthserviceapp.entity.*;
import com.example.healthserviceapp.enums.Rol;
import com.example.healthserviceapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CsrfFlujosTests {
    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ConsultaRepository consultas;
    @Autowired private ObraSocialRepository obras;
    @Autowired private EntityManager em;

    private Paciente paciente;
    private Profesional profesional;
    private Usuario admin;
    private Usuario objetivo;
    private Consulta consulta;
    private ObraSocial cobertura;
    private static final String DIAGNOSTICO = "ETAPA2_CONTENIDO_FICTICIO_NO_REGISTRAR_EN_LOG";

    @BeforeEach
    void preparar() {
        paciente = (Paciente) usuarios.buscarPorEmail("paciente@vitalidapp.com");
        profesional = (Profesional) usuarios.buscarPorEmail("profesional@vitalidapp.com");
        admin = usuarios.buscarPorEmail("admin@admin.com");
        objetivo = new Usuario();
        objetivo.setEmail("objetivo@etapa2.test");
        objetivo.setActivo(false);
        objetivo.setRol(Rol.PACIENTE);
        usuarios.save(objetivo);
        cobertura = obras.save(new ObraSocial("Cobertura etapa 2", 100.0));
        consulta = new Consulta();
        consulta.setPaciente(paciente);
        consulta.setProfesional(profesional);
        consulta.setFecha(LocalDate.now().plusDays(1).toString());
        consulta.setHorario(9);
        consulta.setPrecio(6450.0);
        consultas.saveAndFlush(consulta);
        // En una sesión HTTP real el perfil procede de una solicitud anterior.
        // No debe compartir la instancia administrada por la transacción del guardado.
        em.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservar", "cancelar", "diagnostico", "calificar", "baja", "alta", "rol",
            "crear", "eliminar", "precio", "nombre", "registro", "login", "logout", "perfilPaciente", "perfilProfesional"})
    void postSinTokenSeRechazaSinModificarDatos(String caso) throws Exception {
        List<Object> antes = estado();
        mvc.perform(operacion(caso, HttpMethod.POST)).andExpect(status().isForbidden());
        assertThat(estado()).isEqualTo(antes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservar", "cancelar", "diagnostico", "calificar", "baja", "alta", "rol",
            "crear", "eliminar", "precio", "nombre", "registro", "login", "logout", "perfilPaciente", "perfilProfesional"})
    void postConTokenIncorrectoSeRechazaSinModificarDatos(String caso) throws Exception {
        List<Object> antes = estado();
        mvc.perform(operacion(caso, HttpMethod.POST).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(estado()).isEqualTo(antes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservar", "cancelar", "diagnostico", "calificar", "baja", "alta", "rol",
            "crear", "eliminar", "precio", "nombre"})
    void getNoEjecutaOperacionesDeEscritura(String caso) throws Exception {
        List<Object> antes = estado();
        mvc.perform(operacion(caso, HttpMethod.GET)).andExpect(status().isMethodNotAllowed());
        assertThat(estado()).isEqualTo(antes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservar", "cancelar", "diagnostico", "calificar", "baja", "alta", "rol",
            "crear", "eliminar", "precio", "nombre", "registro", "perfilPaciente", "perfilProfesional"})
    void postConTokenValidoConservaLosFlujos(String caso, CapturedOutput output) throws Exception {
        if (caso.equals("baja")) {
            objetivo.setActivo(true);
            usuarios.saveAndFlush(objetivo);
        }
        long totalConsultas = consultas.count();
        MvcResult resultado = mvc.perform(operacion(caso, HttpMethod.POST).with(csrf()))
                .andExpect(caso.equals("registro") ? status().isOk() : status().is3xxRedirection())
                .andReturn();
        em.flush();
        em.clear();
        switch (caso) {
            case "reservar":
                assertThat(consultas.count()).isEqualTo(totalConsultas + 1);
                break;
            case "cancelar":
                assertThat(consultas.existsById(consulta.getId())).isFalse();
                assertThat(resultado.getResponse().getRedirectedUrl()).isEqualTo("/paciente/consulta");
                break;
            case "diagnostico":
                assertThat(consultas.findById(consulta.getId()).get().getDiagnostico()).isEqualTo(DIAGNOSTICO);
                assertThat(resultado.getResponse().getRedirectedUrl())
                        .isEqualTo("/consulta/consultas?idPaciente=" + paciente.getId());
                assertThat(output.getAll()).doesNotContain(DIAGNOSTICO);
                break;
            case "calificar":
                assertThat(consultas.findById(consulta.getId()).get().getCalificacion()).isEqualTo(5);
                break;
            case "baja":
            case "alta":
                assertThat(usuarios.findById(objetivo.getId()).get().getActivo()).isEqualTo(caso.equals("alta"));
                break;
            case "rol":
                assertThat(usuarios.findById(objetivo.getId()).get().getRol()).isEqualTo(Rol.PROFESIONAL);
                break;
            case "crear":
                assertThat(obras.buscarPorNombre("Nueva etapa 2")).isPresent();
                break;
            case "eliminar":
                assertThat(obras.existsById(cobertura.getId())).isFalse();
                break;
            case "precio":
                assertThat(obras.findById(cobertura.getId()).get().getPrecio()).isEqualTo(125.0);
                break;
            case "nombre":
                assertThat(obras.findById(cobertura.getId()).get().getNombre()).isEqualTo("Renombrada etapa 2");
                break;
            case "registro":
                assertThat(usuarios.buscarPorEmailOptional("registro@etapa2.test")).isPresent();
                break;
            case "perfilPaciente":
                assertThat(((Paciente) usuarios.findById(paciente.getId()).get()).getNombre()).isEqualTo("SofiaEditada");
                break;
            case "perfilProfesional":
                assertThat(((Profesional) usuarios.findById(profesional.getId()).get()).getNombre()).isEqualTo("LauraEditada");
                break;
            default: throw new IllegalArgumentException(caso);
        }
    }

    @Test
    void tokenRenderizadoPermiteLoginYLogoutPeroGetNoCierraSesion() throws Exception {
        MvcResult inicio = mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        MockHttpSession sesion = (MockHttpSession) inicio.getRequest().getSession(false);
        String token = tokenDelHtml(inicio);
        mvc.perform(post("/logincheck").session(sesion).param("email", paciente.getEmail())
                        .param("password", "123456").param("_csrf", token))
                .andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(paciente.getEmail()));
        MvcResult perfil = mvc.perform(get("/paciente/consulta").session(sesion))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/logout").session(sesion)).andExpect(authenticated());
        mvc.perform(post("/logout").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(get("/paciente/consulta").session(sesion)).andExpect(status().isOk()).andExpect(authenticated());
        mvc.perform(post("/logout").session(sesion).param("_csrf", tokenDelHtml(perfil)))
                .andExpect(redirectedUrl("/")).andExpect(unauthenticated());
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void formulariosRenderizadosIncluyenTokenSinColocarloEnLaAccion() throws Exception {
        verificarFormulario(get("/"), "/logincheck");
        verificarFormulario(get("/"), "/registro");
        verificarFormulario(como(paciente, get("/paciente/consulta")), "/consulta/eliminar/" + consulta.getId());
        consulta.setFecha(LocalDate.now().minusDays(1).toString());
        consultas.saveAndFlush(consulta);
        verificarFormulario(como(paciente, get("/paciente/consulta")), "/consulta/calificar/" + consulta.getId());
        verificarFormulario(como(paciente, get("/consulta/horario").param("idProfesional", profesional.getId())
                .param("fecha", LocalDate.now().plusDays(2).toString())), "/consulta/reservar");
        verificarFormulario(como(profesional, get("/consulta/consultas").param("idPaciente", paciente.getId())), "/consulta/diagnostico");
        verificarFormulario(como(admin, get("/admin/dashboard")), "/admin/rol/" + objetivo.getId());
        verificarFormulario(como(admin, get("/obras_sociales")), "/obras_sociales/crear");
        verificarFormulario(como(paciente, get("/perfil/datos")), "/paciente/registro");
        verificarFormulario(como(profesional, get("/profesional/perfil")), "/profesional/registro");
    }

    private void verificarFormulario(MockHttpServletRequestBuilder request, String action) throws Exception {
        String html = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher formularios = Pattern.compile("<form\\b([^>]*)>([\\s\\S]*?)</form>").matcher(html);
        boolean encontrado = false;
        while (formularios.find()) {
            String atributos = formularios.group(1);
            if (atributos.contains("method=\"POST\"")) {
                assertThat(formularios.group(2)).containsPattern("name=\"_csrf\"[^>]*value=\"[^\"]+\"");
                assertThat(atributos).doesNotContain("_csrf");
                encontrado |= atributos.contains("action=\"" + action + "\"");
            }
        }
        assertThat(encontrado).as("Formulario POST %s", action).isTrue();
    }

    private String tokenDelHtml(MvcResult resultado) throws Exception {
        Matcher matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(resultado.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private List<Object> estado() {
        em.flush();
        em.clear();
        Consulta actual = consultas.findById(consulta.getId()).orElse(null);
        Usuario cuenta = usuarios.findById(objetivo.getId()).get();
        ObraSocial obra = obras.findById(cobertura.getId()).orElse(null);
        return Arrays.asList(consultas.count(), actual == null ? null : actual.getDiagnostico(),
                actual == null ? null : actual.getCalificacion(), usuarios.count(), cuenta.getActivo(), cuenta.getRol(),
                obras.count(), obra == null ? null : obra.getNombre(), obra == null ? null : obra.getPrecio(),
                ((Paciente) usuarios.findById(paciente.getId()).get()).getNombre(),
                ((Profesional) usuarios.findById(profesional.getId()).get()).getNombre());
    }

    private MockHttpServletRequestBuilder como(Usuario actor, MockHttpServletRequestBuilder request) {
        return request.with(user(actor.getEmail()).roles(actor.getRol().name())).sessionAttr("usuariosession", actor);
    }

    private MockHttpServletRequestBuilder operacion(String caso, HttpMethod metodo) {
        switch (caso) {
            case "reservar": return como(paciente, request(metodo, "/consulta/reservar")
                    .param("idProfesional", profesional.getId()).param("fecha", LocalDate.now().plusDays(2).toString()).param("horario", "10"));
            case "cancelar": return como(paciente, request(metodo, "/consulta/eliminar/" + consulta.getId()));
            case "diagnostico": return como(profesional, request(metodo, "/consulta/diagnostico")
                    .param("id", consulta.getId()).param("idPaciente", paciente.getId()).param("diagnostico", DIAGNOSTICO));
            case "calificar": return como(paciente, request(metodo, "/consulta/calificar/" + consulta.getId()).param("calificacion", "5"));
            case "baja": return como(admin, request(metodo, "/admin/baja/" + objetivo.getId()));
            case "alta": return como(admin, request(metodo, "/admin/alta/" + objetivo.getId()));
            case "rol": return como(admin, request(metodo, "/admin/rol/" + objetivo.getId()).param("rol", "PROFESIONAL"));
            case "crear": return como(admin, request(metodo, "/obras_sociales/crear").param("nombre", "Nueva etapa 2").param("precio", "125"));
            case "eliminar": return como(admin, request(metodo, "/obras_sociales/eliminar/" + cobertura.getId()));
            case "precio": return como(admin, request(metodo, "/obras_sociales/modificarPrecio").param("id", cobertura.getId()).param("precio", "125"));
            case "nombre": return como(admin, request(metodo, "/obras_sociales/modificarNombre").param("id", cobertura.getId()).param("nombre", "Renombrada etapa 2"));
            case "registro": return request(metodo, "/registro").param("email", "registro@etapa2.test")
                    .param("password", "clave-ficticia").param("password2", "clave-ficticia");
            case "login": return request(metodo, "/logincheck").param("email", paciente.getEmail()).param("password", "123456");
            case "logout": return como(paciente, request(metodo, "/logout"));
            case "perfilPaciente": return como(paciente, multipart("/paciente/registro")
                    .file(new MockMultipartFile("imagen", "", "application/octet-stream", new byte[0]))
                    .param("nombre", "SofiaEditada").param("apellido", "Torres").param("sexo", "FEMENINO")
                    .param("fechaNacimiento", "1992-04-18").param("domicilio", "Domicilio ficticio").param("dni", "30123456")
                    .param("obraSocialNombre", "OSDE"));
            case "perfilProfesional": return como(profesional, multipart("/profesional/registro")
                    .file(new MockMultipartFile("imagen", "", "application/octet-stream", new byte[0]))
                    .param("nombre", "LauraEditada").param("apellido", "Fernandez").param("sexo", "FEMENINO")
                    .param("fechaNacimiento", "1985-09-12").param("domicilio", "Domicilio ficticio").param("dni", "28765432")
                    .param("provincia", "BUENOS_AIRES").param("matricula", "MP-45821").param("especialidad", "CARDIOLOGIA")
                    .param("diasLaborales", "LUNES").param("entrada", "9").param("inicio_descanso", "13")
                    .param("fin_descanso", "14").param("salida", "18").param("precio", "6500"));
            default: throw new IllegalArgumentException(caso);
        }
    }
}
