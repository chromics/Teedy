package com.sismics.docs.rest.resource;

import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.UserRequestDao;
import com.sismics.docs.core.model.jpa.UserRequest;
import com.sismics.docs.core.service.UserRequestService;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.util.List;
import jakarta.ws.rs.core.MediaType;

/**
 * REST Resource for user registration requests.
 */
@Path("/userrequest")
public class UserRequestResource extends BaseResource {

    private final UserRequestService userRequestService = new UserRequestService();
    private final UserRequestDao userRequestDao = new UserRequestDao();
    private final UserDao userDao = new UserDao();

    @PUT
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    public Response createUserRequest(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("email") String email) {

        try {
            // Validate inputs
            if (username == null || username.isEmpty()) {
                throw new ClientException("ValidationError", "Username cannot be empty");
            }
            if (password == null || password.isEmpty()) {
                throw new ClientException("ValidationError", "Password cannot be empty");
            }
            if (email == null || email.isEmpty()) {
                throw new ClientException("ValidationError", "Email cannot be empty");
            }

            // Check if username already exists among active users
            if (userDao.getActiveByUsername(username) != null) {
                throw new ClientException("AlreadyExistingUsername", "Username already used by an active user");
            }

            // Check if email already exists among active users
            if (userDao.getActiveByEmail(email) != null) {
                throw new ClientException("AlreadyExistingEmail", "Email already used by an active user");
            }

            // Check if username already exists among pending user requests
            if (userRequestDao.getRequestByUsername(username) != null) {
                throw new ClientException("AlreadyExistingUsername", "Username already used in a pending request");
            }

            // Check if email already exists among pending user requests
            if (userRequestDao.getRequestByEmail(email) != null) {
                throw new ClientException("AlreadyExistingEmail", "Email already used in a pending request");
            }

            // Create UserRequest object
            UserRequest request = new UserRequest();
            request.setUsername(username);
            request.setEmail(email);
            request.setPassword(password);

            // Call service to create request
            UserRequest created = userRequestService.createUserRequest(request);

            // Build JSON response
            JsonObjectBuilder response = Json.createObjectBuilder()
                    .add("status", "ok")
                    .add("id", created.getId())
                    .add("username", created.getUsername())
                    .add("email", created.getEmail());

            return Response.ok().entity(response.build()).build();
        } catch (IllegalArgumentException e) {
            throw new ClientException("ValidationError", e.getMessage());
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServerException("ServerError", "Error creating user request: " + e.getMessage(), e);
        }
    }

    /**
     * Returns all pending user requests.
     *
     * @api {get} /userrequest List all pending requests
     * @apiName GetUserRequest
     * @apiGroup UserRequest
     * @apiSuccess {Object[]} requests List of requests
     * @apiError (client) ForbiddenError Access denied
     * @apiPermission admin
     * @apiVersion 1.5.0
     */
    @GET
    public Response list() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        List<UserRequest> userRequests = userRequestDao.findAllPending();

        JsonArrayBuilder requests = Json.createArrayBuilder();
        for (UserRequest userRequest : userRequests) {
            requests.add(Json.createObjectBuilder()
                    .add("id", userRequest.getId())
                    .add("username", userRequest.getUsername())
                    .add("email", userRequest.getEmail())
                    .add("create_date", userRequest.getCreateDate().getTime()));
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("requests", requests);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Get a specific user request by ID.
     */
    @GET
    @Path("{id:[a-z0-9\\-]+}")
    public Response get(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        // Get the user request
        UserRequest userRequest = userRequestDao.getById(id);
        if (userRequest == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("id", userRequest.getId())
                .add("username", userRequest.getUsername())
                .add("email", userRequest.getEmail())
                .add("create_date", userRequest.getCreateDate().getTime())
                .add("status", userRequest.getStatus());
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Approve a user request.
     */
    @PUT
    @Path("{id:[a-z0-9\\-]+}/approve")
    public Response approve(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        try {
            UserRequest userRequest = userRequestService.approveUserRequest(id, principal.getId());
            if (userRequest == null) {
                throw new ServerException("ApprovalError", "Error approving user request");
            }

            JsonObjectBuilder response = Json.createObjectBuilder()
                    .add("status", "ok")
                    .add("id", userRequest.getId())
                    .add("username", userRequest.getUsername())
                    .add("request_status", userRequest.getStatus());
            return Response.ok().entity(response.build()).build();
        } catch (IllegalStateException e) {
            throw new ClientException("InvalidRequest", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServerException("ApprovalError", "Error approving user request", e);
        }
    }

    /**
     * Reject a user request.
     */
    @PUT
    @Path("{id:[a-z0-9\\-]+}/reject")
    public Response reject(@PathParam("id") String id) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        try {
            String deletedId = userRequestService.rejectUserRequest(id);
            if (deletedId == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Request not found or not in pending state.\"}")
                        .build();
            }

            JsonObjectBuilder response = Json.createObjectBuilder()
                    .add("status", "ok")
                    .add("id", deletedId)
                    .add("request_status", "deleted");
            return Response.ok().entity(response.build()).build();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServerException("RejectError", "Error rejecting user request", e);
        }
    }

}