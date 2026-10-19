package com.example.healthserviceapp;

import com.example.healthserviceapp.entity.*;
import com.example.healthserviceapp.enums.Rol;
import com.example.healthserviceapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
class ReservaValidacionTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ConsultaRepository consultas;
    @Autowired PacienteRepository pacientes;
    @Autowired ProfesionalRepository profesionales;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean Clock reservaClock;

    Paciente paciente;
    Profesional profesional;

    @BeforeEach
    void preparar() {
        // Lunes, 10:30 en Buenos Aires. No depende del día/hora de CI.
        when(reservaClock.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(reservaClock.instant()).thenReturn(Instant.parse("2030-01-07T13:30:00Z"));
        paciente = (Paciente) usuarios.buscarPorEmail("paciente@vitalidapp.com");
        profesional = (Profesional) usuarios.buscarPorEmail("profesional@vitalidapp.com");
    }

    @ParameterizedTest
    @CsvSource({"2030-01-06,11", "2030-04-08,11", "2030-02-30,11", "no-es-fecha,11",
            "2030-1-08,11", "2030-01-12,11", "2030-01-08,-1", "2030-01-08,24",
            "2030-01-08,8", "2030-01-08,13", "2030-01-08,18", "2030-01-07,9", "2030-01-07,10"})
    void rechazaDatosManipuladosSinGuardar(String fecha, String hora) throws Exception {
        rechazada(reserva(paciente, profesional, fecha, hora));
    }

    @Test
    void rechazaInicioExactoDelTurno() throws Exception {
        when(reservaClock.instant()).thenReturn(Instant.parse("2030-01-07T14:00:00Z"));
        rechazada(reserva(paciente, profesional, "2030-01-07", "11"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactivo", "rol", "sinAgenda", "agendaInvalida", "sinPrecio", "sinCobertura"})
    void rechazaPerfilesNoReservables(String caso) throws Exception {
        switch (caso) {
            case "inactivo": profesional.setActivo(false); break;
            case "rol": profesional.setRol(Rol.PACIENTE); break;
            case "sinAgenda": profesional.setDisponibilidad(null); break;
            case "agendaInvalida": profesional.getDisponibilidad().setInicioDescanso(20); break;
            case "sinPrecio": profesional.setPrecioConsulta(null); break;
            case "sinCobertura": paciente.setObraSocial(null); break;
            default: throw new IllegalArgumentException(caso);
        }
        em.flush();
        rechazada(reserva(paciente, profesional, "2030-01-08", "11"));
    }

    @Test
    void profesionalInexistenteNoProduceExitoSilencioso() throws Exception {
        rechazada(como(paciente, post("/consulta/reservar").param("idProfesional", "inexistente")
                .param("fecha", "2030-01-08").param("horario", "11")));
    }

    @Test
    void parametrosAusentesOMalTipadosSon400() throws Exception {
        long antes = consultas.count();
        mvc.perform(como(paciente, post("/consulta/reservar").param("idProfesional", profesional.getId())
                .param("fecha", "2030-01-08"))).andExpect(status().isBadRequest());
        mvc.perform(reserva(paciente, profesional, "2030-01-08", "texto")).andExpect(status().isBadRequest());
        assertThat(consultas.count()).isEqualTo(antes);
    }

    @ParameterizedTest
    @CsvSource({"2030-01-07,11", "2030-01-08,9", "2030-01-08,12", "2030-01-08,14", "2030-01-08,17"})
    void reservaValidaConfirmaDatosYPrecio(String fecha, String hora) throws Exception {
        mvc.perform(reserva(paciente, profesional, fecha, hora))
                .andExpect(redirectedUrl("/paciente/consulta")).andExpect(flash().attributeExists("exito"));
        em.flush(); em.clear();
        List<Consulta> guardadas = consultas.turnosOcupados(profesional.getId(), paciente.getId(), fecha, fecha);
        assertThat(guardadas).hasSize(1);
        assertThat(guardadas.get(0).getPrecio()).isEqualTo(6450.0);
        assertThat(guardadas.get(0).getHorario()).isEqualTo(Integer.valueOf(hora));
    }

    @Test
    void limiteDe90DiasEsInclusivoYPrecioNoEsNegativo() throws Exception {
        profesional.getDisponibilidad().setDias(new String[]{"DOMINGO"});
        profesional.setPrecioConsulta(10.0);
        em.flush();
        mvc.perform(como(paciente, get("/consulta/profesional").param("provincia", "BUENOS_AIRES")
                        .param("especialidad", "CARDIOLOGIA")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Laura Fernandez · $0.0")));
        mvc.perform(reserva(paciente, profesional, "2030-04-07", "9"))
                .andExpect(redirectedUrl("/paciente/consulta"));
        assertThat(consultas.turnosOcupados(profesional.getId(), paciente.getId(), "2030-04-07", "2030-04-07")
                .get(0).getPrecio()).isZero();
    }

    @Test
    void calendarioYHorariosUsanLasMismasReglas() throws Exception {
        mvc.perform(reserva(paciente, profesional, "2030-01-07", "11"));
        mvc.perform(como(paciente, get("/consulta/horario").param("idProfesional", profesional.getId())
                        .param("fecha", "2030-01-07")))
                .andExpect(model().attribute("horarios", Arrays.asList(12,14,15,16,17)));
        when(reservaClock.instant()).thenReturn(Instant.parse("2030-01-07T21:00:00Z"));
        MvcResult resultado = mvc.perform(como(paciente, get("/consulta/disponibilidad")
                .param("idProfesional", profesional.getId()))).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked") List<String> fechas = (List<String>) resultado.getModelAndView().getModel().get("fechas");
        assertThat(fechas).doesNotContain("2030-01-07", "2030-01-12").contains("2030-01-08");
        mvc.perform(como(paciente, get("/consulta/horario").param("idProfesional", profesional.getId())
                        .param("fecha", "2030-01-07")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No quedan horarios disponibles")));
    }

    @Test
    void dobleEnvioNoDuplicaYErrorEsVisible() throws Exception {
        mvc.perform(reserva(paciente, profesional, "2030-01-08", "11"))
                .andExpect(redirectedUrl("/paciente/consulta"));
        MvcResult rechazo = rechazada(reserva(paciente, profesional, "2030-01-08", "11"));
        mvc.perform(como(paciente, get("/consulta/provincia")).flashAttrs(rechazo.getFlashMap()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ese horario ya está ocupado")));
    }

    @Test
    void fechaMalformadaEnPasoHorarioSeRecuperaSin500() throws Exception {
        mvc.perform(como(paciente, get("/consulta/horario").param("idProfesional", profesional.getId())
                        .param("fecha", "incorrecta")))
                .andExpect(redirectedUrl("/consulta/provincia")).andExpect(flash().attributeExists("error"));
    }

    @Test
    void diaCompletoDesapareceDelCalendario() throws Exception {
        for (int hora : new int[]{9,10,11,12,14,15,16,17}) {
            mvc.perform(reserva(paciente, profesional, "2030-01-08", String.valueOf(hora)))
                    .andExpect(redirectedUrl("/paciente/consulta"));
        }
        MvcResult resultado = mvc.perform(como(paciente, get("/consulta/disponibilidad")
                .param("idProfesional", profesional.getId()))).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked") List<String> fechas = (List<String>) resultado.getModelAndView().getModel().get("fechas");
        assertThat(fechas).doesNotContain("2030-01-08");
    }

    @Test
    void turnoDelPacienteConOtroProfesionalOcultaYBloqueaLaHora() throws Exception {
        Profesional otro = new Profesional(); otro.setEmail("otro@etapa3.test");
        profesionales.save(otro);
        Consulta previa = new Consulta(); previa.setPaciente(paciente); previa.setProfesional(otro);
        previa.setFecha("2030-01-08"); previa.setHorario(11); consultas.saveAndFlush(previa);
        rechazada(reserva(paciente, profesional, "2030-01-08", "11"));
        mvc.perform(como(paciente, get("/consulta/horario").param("idProfesional", profesional.getId())
                        .param("fecha", "2030-01-08")))
                .andExpect(model().attribute("horarios", Arrays.asList(9,10,12,14,15,16,17)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosConfirmacionesSimultaneasDanUnExitoYUnMensajeRecuperable() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrera = new CyclicBarrier(2);
        try {
            Callable<MvcResult> confirmar = () -> {
                barrera.await(10, TimeUnit.SECONDS);
                return mvc.perform(reserva(paciente, profesional, "2030-01-10", "11"))
                        .andExpect(status().is3xxRedirection()).andReturn();
            };
            Future<MvcResult> uno = pool.submit(confirmar);
            Future<MvcResult> dos = pool.submit(confirmar);
            List<MvcResult> resultados = Arrays.asList(uno.get(20, TimeUnit.SECONDS), dos.get(20, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(r -> r.getFlashMap().containsKey("exito")).count()).isEqualTo(1);
            assertThat(resultados.stream().filter(r -> r.getFlashMap().containsKey("error")).count()).isEqualTo(1);
            assertThat(consultas.turnosOcupados(profesional.getId(), paciente.getId(), "2030-01-10", "2030-01-10")).hasSize(1);
        } finally {
            pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS);
            new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                    consultas.deleteAll(consultas.turnosOcupados(profesional.getId(), paciente.getId(), "2030-01-10", "2030-01-10")));
        }
    }

    @Test
    void reservaNoPermitePacienteAjenoAunqueSeCambieLaSesion() throws Exception {
        Paciente ajeno = new Paciente(); ajeno.setId("ajeno");
        mvc.perform(reserva(paciente, profesional, "2030-01-08", "11").sessionAttr("usuariosession", ajeno))
                .andExpect(status().isForbidden());
    }

    // Transacciones reales e independientes: no queda un rollback de prueba ocultando el resultado.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void restriccionesProtegenInsercionesConcurrentesInclusoSinServicio(boolean mismoProfesional) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Paciente otro = tx.execute(s -> {
            Paciente p = new Paciente(); p.setEmail("concurrencia-" + UUID.randomUUID() + "@test.local");
            p.setRol(Rol.PACIENTE); p.setActivo(true); return pacientes.saveAndFlush(p);
        });
        Profesional otroProfesional = tx.execute(s -> {
            Profesional p = new Profesional(); p.setEmail("concurrencia-" + UUID.randomUUID() + "@test.local");
            p.setRol(Rol.PROFESIONAL); p.setActivo(true); return profesionales.saveAndFlush(p);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrera = new CyclicBarrier(2);
        try {
            List<Future<Boolean>> intentos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final int intento = i;
                intentos.add(pool.submit(() -> {
                    barrera.await(10, TimeUnit.SECONDS);
                    try {
                        tx.executeWithoutResult(s -> {
                            Consulta c = new Consulta();
                            c.setPaciente(mismoProfesional && intento == 1 ? otro : paciente);
                            c.setProfesional(!mismoProfesional && intento == 1 ? otroProfesional : profesional);
                            c.setFecha("2030-01-09"); c.setHorario(11); c.setPrecio(100.0);
                            consultas.saveAndFlush(c);
                        });
                        return true;
                    } catch (DataIntegrityViolationException ex) { return false; }
                }));
            }
            assertThat(Arrays.asList(intentos.get(0).get(20, TimeUnit.SECONDS), intentos.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(consultas.turnosOcupados(profesional.getId(), paciente.getId(), "2030-01-09", "2030-01-09")).hasSize(1);
        } finally {
            pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS);
            tx.executeWithoutResult(s -> {
                consultas.deleteAll(consultas.turnosOcupados(profesional.getId(), paciente.getId(), "2030-01-09", "2030-01-09"));
                consultas.flush(); pacientes.deleteById(otro.getId()); profesionales.deleteById(otroProfesional.getId());
            });
        }
    }

    private MockHttpServletRequestBuilder como(Paciente actor, MockHttpServletRequestBuilder request) {
        return request.with(user(actor.getEmail()).roles("PACIENTE")).with(csrf()).sessionAttr("usuariosession", actor);
    }

    private MockHttpServletRequestBuilder reserva(Paciente actor, Profesional medico, String fecha, String hora) {
        return como(actor, post("/consulta/reservar").param("idProfesional", medico.getId())
                .param("fecha", fecha).param("horario", hora));
    }

    private MvcResult rechazada(MockHttpServletRequestBuilder request) throws Exception {
        long antes = consultas.count();
        MvcResult resultado = mvc.perform(request).andExpect(redirectedUrl("/consulta/provincia"))
                .andExpect(flash().attributeExists("error")).andReturn();
        assertThat(consultas.count()).isEqualTo(antes);
        return resultado;
    }
}
