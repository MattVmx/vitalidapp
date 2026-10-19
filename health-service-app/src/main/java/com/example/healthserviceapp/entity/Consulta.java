package com.example.healthserviceapp.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.GenericGenerator;

import lombok.Data;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_consulta_profesional_turno", columnNames = {"profesional_id", "fecha", "horario"}),
    @UniqueConstraint(name = "uk_consulta_paciente_turno", columnNames = {"paciente_id", "fecha", "horario"})
})
@Data
public class Consulta {
    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;
    
    @OneToOne
    private Profesional profesional;

    @OneToOne
    private Paciente paciente;

    @Column(name = "diagnostico", length = 6000)
    private String diagnostico;

    private String fecha;

    private Integer horario;

    private Double precio;

    private Integer calificacion;
}
