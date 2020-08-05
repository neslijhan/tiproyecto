package com.dnpa.finalproject.depressionsafetytracking.Location;

//Se encarga del manejo de información de los puntos de ubicación guardados
public class MapsPojo {

    //Los datos definidos aqui deben tener el mismo nombre que en la BD firebase
    private double latitud;
    private double longitud;

    public MapsPojo(){
    }
    public double getLatitud() {
        return latitud;
    }
    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }
    public double getLongitud() {
        return longitud;
    }
    public void setLongitud(double longitud) {
        this.longitud = longitud;
    }
}
