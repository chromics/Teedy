package com.sismics.docs.core.dao;

import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.model.jpa.UserRequest;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

import java.util.*;

/**
 * User Request DAO.
 */
public class UserRequestDao {

    /**
     * Creates a new user request.
     *
     * @param userRequest User request to create
     * @return Created user request
     */
    public UserRequest create(UserRequest userRequest) {
        // Validate input
        if (userRequest.getId() == null) {
            userRequest.setId(UUID.randomUUID().toString());
        }

        // Create the user request
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        userRequest.setCreateDate(new Date());
        userRequest.setStatus("PENDING");
        em.persist(userRequest);

        return userRequest;
    }

    /**
     * Updates a user request.
     *
     * @param userRequest User request to update
     * @return Updated user request
     */
    public UserRequest update(UserRequest userRequest) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        return em.merge(userRequest);
    }

    /**
     * Gets a user request by its ID.
     *
     * @param id User request ID
     * @return User request
     */
    public UserRequest getById(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            return em.find(UserRequest.class, id);
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Gets a pending user request by username.
     *
     * @param username Username
     * @return User request
     */
    public UserRequest getRequestByUsername(String username) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            Query q = em.createQuery("select ur from UserRequest ur where ur.username = :username and ur.status = 'PENDING'");
            q.setParameter("username", username);
            return (UserRequest) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Gets a pending user request by email.
     *
     * @param email Email
     * @return User request
     */
    public UserRequest getRequestByEmail(String email) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            Query q = em.createQuery("select ur from UserRequest ur where ur.email = :email and ur.status = 'PENDING'");
            q.setParameter("email", email);
            return (UserRequest) q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Returns all pending user requests.
     *
     * @return List of user requests
     */
    @SuppressWarnings("unchecked")
    public List<UserRequest> findAllPending() {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        Query q = em.createQuery("select ur from UserRequest ur where ur.status = 'PENDING' order by ur.createDate DESC");
        return q.getResultList();
    }

    /**
     * Deletes a user request.
     *
     * @param id User request ID
     */
    public void delete(String id) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        UserRequest ur = em.find(UserRequest.class, id);
        if (ur != null) {
            em.remove(ur);
        }
    }

    // --- User helper methods ---

    public User findUserByUsername(String username) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            Query query = em.createQuery("SELECT u FROM User u WHERE u.username = :username AND u.status = 'PENDING'");
            query.setParameter("username", username);
            return (User) query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public User findUserByEmail(String email) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            Query query = em.createQuery("SELECT u FROM User u WHERE u.email = :email");
            query.setParameter("email", email);
            return (User) query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public User createUser(User user) {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        em.persist(user);
        return user;
    }
}