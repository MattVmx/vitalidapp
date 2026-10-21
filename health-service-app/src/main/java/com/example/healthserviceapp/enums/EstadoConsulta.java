package com.example.healthserviceapp.enums;

public enum EstadoConsulta {
    RESERVADA("Reservada"), CANCELADA("Cancelada"), FINALIZADA("Finalizada");

    private final String etiqueta;

    EstadoConsulta(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
