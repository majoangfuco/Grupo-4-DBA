package com.ecommerceb2b.backend.Entities;


import java.util.Date;


public class UsuarioEntidad {

    private Long usuario_ID;

    private String nombre_Usuario;

    private String correo ;

    private String contrasena ;

    private Date ultima_Compra;

    private String rut_Empresa;

    private String rol;

    public Long getUsuario_ID() {
        return usuario_ID;
    }

    public void setUsuario_ID(Long usuario_ID) {
        this.usuario_ID = usuario_ID;
    }

    public String getNombre_Usuario() {
        return nombre_Usuario;
    }

    public void setNombre_Usuario(String nombre_Usuario) {
        this.nombre_Usuario = nombre_Usuario;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public Date getUltima_Compra() {
        return ultima_Compra;
    }

    public void setUltima_Compra(Date ultima_Compra) {
        this.ultima_Compra = ultima_Compra;
    }

    public String getRut_Empresa() {
        return rut_Empresa;
    }

    public void setRut_Empresa(String rut_Empresa) {
        this.rut_Empresa = rut_Empresa;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}
