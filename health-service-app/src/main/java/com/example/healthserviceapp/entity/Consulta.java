package com.example.healthserviceapp.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Enumerated;
import javax.persistence.EnumType;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Version;
import java.time.LocalDateTime;
import com.example.healthserviceapp.enums.EstadoConsulta;
import lombok.AccessLevel;
import lombok.Setter;

import org.hibernate.annotations.GenericGenerator;

import lombok.Data;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uk_consulta_profesional_activa", columnNames = {"profesional_id", "fecha", "horario", "turno_activo"}),
    @UniqueConstraint(name = "uk_consulta_paciente_activa", columnNames = {"paciente_id", "fecha", "horario", "turno_activo"})
})
@Data
public class Consulta {
    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConsulta estado = EstadoConsulta.RESERVADA;

    // NULL libera la exclusividad sin borrar la consulta. Nunca usar false.
    @Column(name = "turno_activo")
    @Setter(AccessLevel.NONE)
    private Boolean turnoActivo = true;

    private LocalDateTime canceladaEn;

    private LocalDateTime finalizadaEn;

    @PrePersist
    @PreUpdate
    private void sincronizarOcupacion() {
        turnoActivo = estado == EstadoConsulta.CANCELADA ? null : true;
    }
    
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
