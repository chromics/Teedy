package com.sismics.docs.core.dao.dto;

import com.sismics.docs.core.model.jpa.UserRequest;

import java.util.Date;

/**
 * Data Transfer Object for UserRequest.
 */
public class UserRequestDto {
    private String id;
    private String username;
    private String email;
    private Date createDate;
    private String status;

    // Default constructor
    public UserRequestDto() {}

    // Constructor to create DTO from entity
    public UserRequestDto(UserRequest entity) {
        this.id = entity.getId();
        this.username = entity.getUsername();
        this.email = entity.getEmail();
        this.status = entity.getStatus();
        this.createDate = entity.getCreateDate();
    }

    // Convert DTO to entity
    public UserRequest toEntity() {
        UserRequest entity = new UserRequest();
        entity.setId(this.id);
        entity.setUsername(this.username);
        entity.setEmail(this.email);
        entity.setStatus(this.status);
        entity.setCreateDate(this.createDate);
        return entity;
    }

    // Getters and setters
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public Date getCreateDate() {
        return createDate;
    }
    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}
