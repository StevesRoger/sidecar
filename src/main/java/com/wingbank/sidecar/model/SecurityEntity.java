package com.wingbank.sidecar.model;

import java.util.*;

public class SecurityEntity {

    private String id = UUID.randomUUID().toString();
    private Boolean authenticated;
    private Boolean permitAll;
    private Boolean denyAll;
    private int order = -1;
    private Collection<String> endpoints = new HashSet<>();
    private Collection<String> roles = new HashSet<>();
    private Collection<String> methods = new HashSet<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(Boolean authenticated) {
        this.authenticated = authenticated;
    }

    public Boolean getPermitAll() {
        return permitAll;
    }

    public void setPermitAll(Boolean permitAll) {
        this.permitAll = permitAll;
    }

    public Boolean getDenyAll() {
        return denyAll;
    }

    public void setDenyAll(Boolean denyAll) {
        this.denyAll = denyAll;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public Collection<String> getEndpoints() {
        return Optional.ofNullable(endpoints).orElse(new HashSet<>());
    }

    public void setEndpoints(Collection<String> endpoints) {
        this.endpoints = endpoints;
    }

    public Collection<String> getRoles() {
        return Optional.ofNullable(roles).orElse(new HashSet<>());
    }

    public void setRoles(Collection<String> roles) {
        this.roles = roles;
    }

    public Collection<String> getMethods() {
        return Optional.ofNullable(methods).orElse(new HashSet<>());
    }

    public void setMethods(Collection<String> methods) {
        this.methods = methods;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object that) {
        return that instanceof SecurityEntity &&
                this.getId() != null &&
                this.getId().equals(((SecurityEntity) that).getId());
    }

    @Override
    public String toString() {
        return "SecurityEntity{" +
                "id='" + id + '\'' +
                ", authenticated=" + authenticated +
                ", permitAll=" + permitAll +
                ", denyAll=" + denyAll +
                ", order=" + order +
                ", endpoints=" + endpoints +
                ", roles=" + roles +
                ", methods=" + methods +
                '}';
    }
}
