package com.example.healthserviceapp.initializer;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.healthserviceapp.Exceptions.MiException;
import com.example.healthserviceapp.entity.Disponibilidad;
import com.example.healthserviceapp.entity.ObraSocial;
import com.example.healthserviceapp.entity.Paciente;
import com.example.healthserviceapp.entity.Profesional;
import com.example.healthserviceapp.enums.Especialidad;
import com.example.healthserviceapp.enums.Provincias;
import com.example.healthserviceapp.enums.Rol;
import com.example.healthserviceapp.enums.Sexo;
import com.example.healthserviceapp.service.DisponibilidadService;
import com.example.healthserviceapp.service.ImagenService;
import com.example.healthserviceapp.service.ObraSocialService;
import com.example.healthserviceapp.service.PacienteService;
import com.example.healthserviceapp.service.ProfesionalService;
import com.example.healthserviceapp.service.UsuarioService;

@Component
public class DataInitializer implements CommandLineRunner {
    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ObraSocialService obraSocialService;

    @Autowired
    private PacienteService pacienteService;

    @Autowired
    private ImagenService imagenService;

    @Autowired
    private ProfesionalService profesionalService;

    @Autowired
    private DisponibilidadService disponibilidadService;

    public DataInitializer() {
    }

    @Override
    public void run(String... args) throws MiException {
        usuarioService.crearUsuario("admin@admin.com", "123456", Rol.ADMIN);

        obraSocialService.crearObraSocial("PAMI", 10d);
        obraSocialService.crearObraSocial("IOMA", 20d);
        obraSocialService.crearObraSocial("UOM", 30d);
        obraSocialService.crearObraSocial("OSECAC", 40d);
        obraSocialService.crearObraSocial("OSDE", 50d);

        if (pacienteService.contarPacientes() < 1) {
            crearPacienteDemo();
        }

        int profesionalesExistentes = profesionalService.contarProfesionales();
        if (profesionalesExistentes < 24) {
            if (profesionalesExistentes == 0) {
                crearProfesionalDemo();
                profesionalesExistentes++;
            }
            for (int i = profesionalesExistentes; i < 24; i++) {
                crearProfesional();
            }
        }
    }

    private void crearPacienteDemo() {
        Paciente paciente = new Paciente();
        paciente.setActivo(true);
        paciente.setNombre("Sofia");
        paciente.setApellido("Torres");
        paciente.setDni(30123456);
        paciente.setDomicilio("Mar del Plata, Buenos Aires");
        paciente.setEmail("paciente@vitalidapp.com");
        paciente.setFechaNacimiento(new Date());
        paciente.setImagen(imagenService.defaultImagen());
        paciente.setObraSocial(obraSocialService.buscarPorNombre("OSDE"));
        paciente.setPassword(new BCryptPasswordEncoder().encode("123456"));
        paciente.setRol(Rol.PACIENTE);
        paciente.setSexo(Sexo.FEMENINO);
        pacienteService.crearPaciente(paciente);
    }

    private void crearProfesionalDemo() {
        Profesional profesional = new Profesional();
        profesional.setActivo(true);
        profesional.setNombre("Laura");
        profesional.setApellido("Fernandez");
        profesional.setDni(28765432);
        profesional.setDomicilio("Mar del Plata, Buenos Aires");
        profesional.setEmail("profesional@vitalidapp.com");
        profesional.setFechaNacimiento(new Date());
        profesional.setImagen(imagenService.defaultImagen());
        profesional.setMatricula("MP-45821");
        profesional.setEspecialidad(Especialidad.CARDIOLOGIA);
        profesional.setPassword(new BCryptPasswordEncoder().encode("123456"));
        profesional.setRol(Rol.PROFESIONAL);
        profesional.setSexo(Sexo.FEMENINO);

        Disponibilidad disponibilidad = new Disponibilidad();
        disponibilidad.setEntrada(9);
        disponibilidad.setInicioDescanso(13);
        disponibilidad.setFinDescanso(14);
        disponibilidad.setSalida(18);
        disponibilidad.setDias(new String[] { "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES" });
        profesional.setDisponibilidad(disponibilidadService.guardar(disponibilidad));
        profesional.setProvincia(Provincias.BUENOS_AIRES);
        profesional.setPrecioConsulta(6500d);
        profesional.setCalificacion(4.8d);
        profesionalService.crearProfesional(profesional);
    }

    private Object randomOfArray(Object[] array) {
        int randomIndex = ThreadLocalRandom.current().nextInt(array.length);
        return array[randomIndex];
    }

    private Integer intRandom(Integer min, Integer max) {
        Random random = new Random();
        int numero = random.nextInt(max - min) + min;
        return numero;
    }

    private ObraSocial randomObraSocial() {
        List<ObraSocial> lista = obraSocialService.listarObrasSociales();
        int numero = intRandom(0, lista.size());
        return lista.get(numero);
    }

    private void crearProfesional() {
        String[] nombres = { "Antonio", "Luis", "Carlos", "Miguel", "Juan", "Jorge", "Francisco", "Pablo", "Roberto",
                "Javier",
                "Raquel", "Laura", "Carolina", "Marta", "Teresa", "Natalia", "Isabel", "Maria", "Cristina", "Ana" };
        String[] apellidos = { "Gomez", "Fernandez", "Rodriguez", "Alvarez", "Perez", "Gonzalez", "Martin", "Sanchez",
                "Gutierrez", "Dominguez", "Santos", "Vega", "Campos", "Carrasco", "Castro", "Calvo", "Hernando", "Mora",
                "Soria", "Torres" };

        Profesional profesional = new Profesional();
        profesional.setActivo(true);
        String nombre = randomOfArray(nombres).toString();
        profesional.setNombre(nombre);
        String apellido = randomOfArray(apellidos).toString();
        profesional.setApellido(apellido);
        Integer dni = intRandom(10000000, 90000000);
        profesional.setDni(dni);
        profesional.setDomicilio(nombre + " " + apellido + " " + dni);
        profesional.setEmail(nombre + apellido + dni + "@prueba.com");
        Date fechaNacimiento = new Date();
        profesional.setFechaNacimiento(fechaNacimiento);
        profesional.setImagen(imagenService.defaultImagen());
        profesional.setMatricula(intRandom(100000, 900000).toString());
        Especialidad especialidad = (Especialidad) randomOfArray(Especialidad.values());
        profesional.setEspecialidad(especialidad);
        profesional.setPassword(new BCryptPasswordEncoder().encode("123456"));
        profesional.setRol(Rol.PROFESIONAL);
        profesional.setSexo(Sexo.values()[intRandom(0, 2)]);
        Disponibilidad disponibilidad = new Disponibilidad();
        Integer horaEntrada = intRandom(0, 24);
        Integer horaSalida = horaEntrada + intRandom(0, 24 - horaEntrada);
        Integer inicioDescanso = horaEntrada + intRandom(0, 24 - horaSalida);
        Integer finDescanso = inicioDescanso + 1;
        disponibilidad.setEntrada(horaEntrada);
        disponibilidad.setInicioDescanso(inicioDescanso);
        disponibilidad.setFinDescanso(finDescanso);
        disponibilidad.setSalida(horaSalida);
        disponibilidad.setDias(randomDias());
        profesional.setDisponibilidad(disponibilidadService.guardar(disponibilidad));
        Provincias provincia = (Provincias) randomOfArray(Provincias.values());
        profesional.setProvincia(provincia);
        Double precio = Double.valueOf(intRandom(1000, 10000));
        profesional.setPrecioConsulta(precio);
        profesional.setCalificacion(0d);
        profesionalService.crearProfesional(profesional);
    }

    public String[] randomDias() {
        String[] dias = { "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO" };
        Set<String> diasSeleccionados = new HashSet<String>();
        Random random = new Random();
        while (diasSeleccionados.size() < 3) {
            int indice = random.nextInt(dias.length);
            diasSeleccionados.add(dias[indice]);
        }
        String[] array = new String[diasSeleccionados.size()];
        return diasSeleccionados.toArray(array);
    }
}
