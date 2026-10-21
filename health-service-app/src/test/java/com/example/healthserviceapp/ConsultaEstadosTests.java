package com.example.healthserviceapp;

import com.example.healthserviceapp.entity.*;
import com.example.healthserviceapp.enums.EstadoConsulta;
import com.example.healthserviceapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
class ConsultaEstadosTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ConsultaRepository consultas;
    @Autowired ProfesionalRepository profesionales;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @MockBean Clock reservaClock;
    Paciente paciente;
    Profesional profesional;

    @BeforeEach
    void preparar() {
        when(reservaClock.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(reservaClock.instant()).thenReturn(Instant.parse("2030-01-07T13:30:00Z"));
        paciente = (Paciente) usuarios.buscarPorEmail("paciente@vitalidapp.com");
        profesional = (Profesional) usuarios.buscarPorEmail("profesional@vitalidapp.com");
    }

    @Test
    void cancelarConservaRegistroLiberaHorarioYPermiteReservarVariasVeces() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(reservar()).andExpect(redirectedUrl("/paciente/consulta"));
            Consulta c = consultas.turnosOcupados(profesional.getId(), paciente.getId(), "2030-01-08", "2030-01-08").get(0);
            mvc.perform(como(paciente, post("/consulta/eliminar/" + c.getId())))
                    .andExpect(redirectedUrl("/paciente/consulta"));
            Consulta cancelada = recargar(c);
            assertThat(cancelada.getEstado()).isEqualTo(EstadoConsulta.CANCELADA);
            assertThat(cancelada.getTurnoActivo()).isNull();
            assertThat(cancelada.getPrecio()).isEqualTo(6450.0);
            assertThat(cancelada.getFecha()).isEqualTo("2030-01-08");
            assertThat(cancelada.getHorario()).isEqualTo(9);
            assertThat(cancelada.getCanceladaEn()).isEqualTo(LocalDateTime.of(2030,1,7,10,30));
            LocalDateTime primeraCancelacion = cancelada.getCanceladaEn();
            Long version = cancelada.getVersion();
            mvc.perform(como(paciente, post("/consulta/eliminar/" + c.getId()))).andExpect(status().is3xxRedirection());
            cancelada = recargar(c);
            assertThat(cancelada.getCanceladaEn()).isEqualTo(primeraCancelacion);
            assertThat(cancelada.getVersion()).isEqualTo(version);
        }
        assertThat(consultas.listarConsulta(paciente.getId())).hasSize(3);
        assertThat(consultas.turnosOcupados(profesional.getId(), paciente.getId(), "2030-01-08", "2030-01-08")).isEmpty();
        mvc.perform(como(paciente, get("/consulta/horario").param("idProfesional", profesional.getId()).param("fecha", "2030-01-08")))
                .andExpect(model().attribute("horarios", Arrays.asList(9,10,11,12,14,15,16,17)));
        mvc.perform(como(paciente, get("/paciente/consulta"))).andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(not(containsString("Cancelar turno"))));
        mvc.perform(como(profesional, get("/consulta/consultas").param("idPaciente", paciente.getId())))
                .andExpect(content().string(containsString("Consulta cancelada")))
                .andExpect(content().string(not(containsString("name=\"diagnostico\""))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pasada", "inicio", "finalizada"})
    void noPermiteCancelarConsultaIniciadaOFinalizada(String caso) throws Exception {
        Consulta c = crear("2030-01-07", 9, EstadoConsulta.RESERVADA);
        if (caso.equals("inicio")) when(reservaClock.instant()).thenReturn(Instant.parse("2030-01-07T12:00:00Z"));
        if (caso.equals("finalizada")) { c.setEstado(EstadoConsulta.FINALIZADA); c.setDiagnostico("Ficticio"); consultas.saveAndFlush(c); }
        mvc.perform(como(paciente, post("/consulta/eliminar/" + c.getId()))).andExpect(status().isConflict());
        assertThat(recargar(c).getEstado()).isNotEqualTo(EstadoConsulta.CANCELADA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"futura", "enCurso", "cancelada", "finalizada"})
    void diagnosticoNoPuedeAdelantarseNiSobrescribirse(String caso) throws Exception {
        Consulta c = crear("2030-01-07", caso.equals("futura") ? 11 : caso.equals("enCurso") ? 10 : 9,
                caso.equals("cancelada") ? EstadoConsulta.CANCELADA : caso.equals("finalizada") ? EstadoConsulta.FINALIZADA : EstadoConsulta.RESERVADA);
        String anterior = c.getDiagnostico();
        mvc.perform(diagnostico(c, "Texto que no debe guardarse")).andExpect(status().isConflict());
        assertThat(recargar(c).getDiagnostico()).isEqualTo(anterior);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "largo"})
    void diagnosticoVacioOExcesivoNoFinaliza(String texto) throws Exception {
        Consulta c = crear("2030-01-07",9,EstadoConsulta.RESERVADA);
        mvc.perform(diagnostico(c, texto.equals("largo") ? String.join("", Collections.nCopies(1501, "a")) : texto))
                .andExpect(status().isConflict());
        assertThat(recargar(c).getEstado()).isEqualTo(EstadoConsulta.RESERVADA);
    }

    @Test
    void finalizarAlTerminarLaHoraHabilitaCalificacionUnaSolaVez() throws Exception {
        Consulta c = crear("2030-01-07",9,EstadoConsulta.RESERVADA);
        when(reservaClock.instant()).thenReturn(Instant.parse("2030-01-07T13:00:00Z"));
        mvc.perform(diagnostico(c, "  Observación ficticia de prueba  "))
                .andExpect(redirectedUrl("/consulta/consultas?idPaciente=" + paciente.getId()));
        Consulta finalizada = recargar(c);
        assertThat(finalizada.getEstado()).isEqualTo(EstadoConsulta.FINALIZADA);
        assertThat(finalizada.getDiagnostico()).isEqualTo("Observación ficticia de prueba");
        assertThat(finalizada.getFinalizadaEn()).isEqualTo(LocalDateTime.of(2030,1,7,10,0));
        mvc.perform(como(paciente, get("/paciente/consulta")))
                .andExpect(content().string(containsString("Finalizada")))
                .andExpect(content().string(containsString("Guardar calificación")));
        mvc.perform(calificar(c,5)).andExpect(redirectedUrl("/paciente/consulta"));
        mvc.perform(calificar(c,1)).andExpect(status().isConflict());
        assertThat(recargar(c).getCalificacion()).isEqualTo(5);
        assertThat(profesionales.findById(profesional.getId()).get().getCalificacion()).isEqualTo(5.0);
        mvc.perform(como(paciente, get("/paciente/consulta")))
                .andExpect(content().string(not(containsString("Guardar calificación"))));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1,0,6,100})
    void rangoInvalidoNoCambiaCalificacionNiPromedio(int nota) throws Exception {
        Consulta c = crear("2030-01-07",9,EstadoConsulta.FINALIZADA);
        Double anterior = profesional.getCalificacion();
        mvc.perform(calificar(c,nota)).andExpect(status().isConflict());
        assertThat(recargar(c).getCalificacion()).isNull();
        assertThat(profesionales.findById(profesional.getId()).get().getCalificacion()).isEqualTo(anterior);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVADA", "CANCELADA"})
    void noSeCalificaPorElSoloPasoDelTiempo(String estado) throws Exception {
        Consulta c = crear("2030-01-06",9,EstadoConsulta.valueOf(estado));
        mvc.perform(calificar(c,5)).andExpect(status().isConflict());
        assertThat(recargar(c).getCalificacion()).isNull();
        mvc.perform(como(paciente, get("/paciente/consulta")))
                .andExpect(content().string(not(containsString("Guardar calificación"))));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosDiagnosticosSimultaneosNoSeSobrescriben() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Consulta c = tx.execute(s -> crear("2030-01-05",9,EstadoConsulta.RESERVADA));
        try {
            List<MvcResult> resultados = competir(() -> mvc.perform(diagnostico(c,"Diagnóstico ficticio A")).andReturn(),
                    () -> mvc.perform(diagnostico(c,"Diagnóstico ficticio B")).andReturn());
            assertThat(resultados).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(302,409);
            Consulta guardada = consultas.findById(c.getId()).get();
            assertThat(guardada.getEstado()).isEqualTo(EstadoConsulta.FINALIZADA);
            assertThat(guardada.getDiagnostico()).isIn("Diagnóstico ficticio A","Diagnóstico ficticio B");
        } finally { tx.executeWithoutResult(s -> consultas.deleteById(c.getId())); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void calificacionesConcurrentesMantienenPromedioCorrecto() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Double anterior = profesional.getCalificacion();
        Consulta a = tx.execute(s -> crear("2030-01-04",9,EstadoConsulta.FINALIZADA));
        Consulta b = tx.execute(s -> crear("2030-01-04",10,EstadoConsulta.FINALIZADA));
        try {
            List<MvcResult> resultados = competir(() -> mvc.perform(calificar(a,1)).andReturn(),
                    () -> mvc.perform(calificar(b,5)).andReturn());
            assertThat(resultados).extracting(r -> r.getResponse().getStatus()).containsOnly(302);
            assertThat(profesionales.findById(profesional.getId()).get().getCalificacion()).isEqualTo(3.0);
        } finally {
            tx.executeWithoutResult(s -> {
                consultas.deleteById(a.getId()); consultas.deleteById(b.getId());
                Profesional p = profesionales.findById(profesional.getId()).get(); p.setCalificacion(anterior); profesionales.save(p);
            });
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void calificacionSimultaneaDeLaMismaConsultaSoloGuardaUna() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Double anterior = profesional.getCalificacion();
        Consulta c = tx.execute(s -> crear("2030-01-03",9,EstadoConsulta.FINALIZADA));
        try {
            List<MvcResult> resultados = competir(() -> mvc.perform(calificar(c,1)).andReturn(),
                    () -> mvc.perform(calificar(c,5)).andReturn());
            assertThat(resultados).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(302,409);
            Integer nota = consultas.findById(c.getId()).get().getCalificacion();
            assertThat(nota).isIn(1,5);
            assertThat(profesionales.findById(profesional.getId()).get().getCalificacion()).isEqualTo(nota.doubleValue());
        } finally {
            tx.executeWithoutResult(s -> {
                consultas.deleteById(c.getId());
                Profesional p = profesionales.findById(profesional.getId()).get(); p.setCalificacion(anterior); profesionales.save(p);
            });
        }
    }

    private List<MvcResult> competir(Callable<MvcResult> a, Callable<MvcResult> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrera = new CyclicBarrier(2);
        try {
            Future<MvcResult> primero = pool.submit(() -> { barrera.await(10,TimeUnit.SECONDS); return a.call(); });
            Future<MvcResult> segundo = pool.submit(() -> { barrera.await(10,TimeUnit.SECONDS); return b.call(); });
            return Arrays.asList(primero.get(20,TimeUnit.SECONDS),segundo.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
    }

    private Consulta crear(String fecha, int hora, EstadoConsulta estado) {
        Consulta c = new Consulta(); c.setPaciente(paciente); c.setProfesional(profesional);
        c.setFecha(fecha); c.setHorario(hora); c.setPrecio(6450.0); c.setEstado(estado);
        if (estado == EstadoConsulta.FINALIZADA) { c.setDiagnostico("Observación ficticia anterior"); }
        return consultas.saveAndFlush(c);
    }

    private Consulta recargar(Consulta c) { em.flush(); em.clear(); return consultas.findById(c.getId()).get(); }

    private MockHttpServletRequestBuilder como(Usuario actor, MockHttpServletRequestBuilder request) {
        return request.with(user(actor.getEmail()).roles(actor.getRol().name())).with(csrf()).sessionAttr("usuariosession",actor);
    }

    private MockHttpServletRequestBuilder diagnostico(Consulta c, String texto) {
        return como(profesional,post("/consulta/diagnostico").param("id",c.getId())
                .param("idPaciente",paciente.getId()).param("diagnostico",texto));
    }

    private MockHttpServletRequestBuilder calificar(Consulta c, int nota) {
        return como(paciente,post("/consulta/calificar/"+c.getId()).param("calificacion",String.valueOf(nota)));
    }

    private MockHttpServletRequestBuilder reservar() {
        return como(paciente,post("/consulta/reservar").param("idProfesional",profesional.getId())
                .param("fecha","2030-01-08").param("horario","9"));
    }
}
