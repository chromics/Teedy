package com.sismics.docs.rest.resource;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.AclDao;
import com.sismics.docs.core.dao.AuditLogDao;
import com.sismics.docs.core.dao.criteria.AuditLogCriteria;
import com.sismics.docs.core.dao.dto.AuditLogDto;
import com.sismics.docs.core.util.SecurityUtil;
import com.sismics.docs.core.util.jpa.PaginatedList;
import com.sismics.docs.core.util.jpa.PaginatedLists;
import com.sismics.docs.core.util.jpa.SortCriteria;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.util.JsonUtil;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Audit log REST resources.
 * 
 * @author bgamard
 */
@Path("/auditlog")
public class AuditLogResource extends BaseResource {
    /**
     * Returns the list of all logs for a document or user.
     *
     * @api {get} /auditlog Get audit logs
     * @apiDescription If no document ID is provided, logs for the current user will be returned.
     * @apiName GetAuditlog
     * @apiGroup Auditlog
     * @apiParam {String} [document] Document ID
     * @apiSuccess {String} total Total number of logs
     * @apiSuccess {Object[]} logs List of logs
     * @apiSuccess {String} logs.id ID
     * @apiSuccess {String} logs.username Username
     * @apiSuccess {String} logs.target Entity ID
     * @apiSuccess {String="Acl","Comment","Document","File","Group","Tag","User","RouteModel","Route"} logs.class Entity type
     * @apiSuccess {String="CREATE","UPDATE","DELETE"} logs.type Type
     * @apiSuccess {String} logs.message Message
     * @apiSuccess {Number} logs.create_date Create date (timestamp)
     * @apiError (client) ForbiddenError Access denied
     * @apiError (client) NotFound Document not found
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @return Response
     */
    @GET
    public Response list(@QueryParam("document") String documentId) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // On a document or a user?
        PaginatedList<AuditLogDto> paginatedList = PaginatedLists.create(20, 0);
        SortCriteria sortCriteria = new SortCriteria(1, false);
        AuditLogCriteria criteria = new AuditLogCriteria();
        if (Strings.isNullOrEmpty(documentId)) {
            // Search logs for a user
            criteria.setUserId(principal.getId());
            criteria.setAdmin(SecurityUtil.skipAclCheck(getTargetIdList(null)));
        } else {
            // Check ACL on the document
            AclDao aclDao = new AclDao();
            if (!aclDao.checkPermission(documentId, PermType.READ, getTargetIdList(null))) {
                throw new NotFoundException();
            }
            criteria.setDocumentId(documentId);
        }
        
        // Search the logs
        AuditLogDao auditLogDao = new AuditLogDao();
        auditLogDao.findByCriteria(paginatedList, criteria, sortCriteria);
        
        // Assemble the results
        JsonArrayBuilder logs = Json.createArrayBuilder();
        for (AuditLogDto auditLogDto : paginatedList.getResultList()) {
            logs.add(Json.createObjectBuilder()
                    .add("id", auditLogDto.getId())
                    .add("username", auditLogDto.getUsername())
                    .add("target", auditLogDto.getEntityId())
                    .add("class", auditLogDto.getEntityClass())
                    .add("type", auditLogDto.getType().name())
                    .add("message", JsonUtil.nullable(auditLogDto.getMessage()))
                    .add("create_date", auditLogDto.getCreateTimestamp()));
        }

        // Send the response
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("logs", logs)
                .add("total", paginatedList.getResultCount());
        return Response.ok().entity(response.build()).build();
    }

    /**
     * @api {get} /auditlog/daily-activity Get daily activity logs
     * @apiDescription Returns audit logs for a specific day. Admin-only.
     * @apiName GetDailyActivityLogs
     * @apiGroup Auditlog
     * @apiParam {String} [date] Date in format yyyy-MM-dd. Defaults to today.
     * @apiSuccess {String} total Total number of logs
     * @apiSuccess {Object[]} logs List of logs
     * @apiSuccess {String} logs.id ID
     * @apiSuccess {String} logs.username Username
     * @apiSuccess {String} logs.target Entity ID
     * @apiSuccess {String} logs.class Entity type
     * @apiSuccess {String="CREATE","UPDATE","DELETE"} logs.type Type
     * @apiSuccess {String} logs.message Message
     * @apiSuccess {Number} logs.create_date Create date (timestamp)
     * @apiError (client) ForbiddenError Access denied
     * @apiPermission admin
     * @apiVersion 1.5.0
     *
     * @return Response
     */
    @GET
    @Path("/daily-activity")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDailyActivity(@QueryParam("date") String dateStr) {
        // be authenticated
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // be admin
        if (!hasBaseFunction(BaseFunction.ADMIN)) {
            throw new ForbiddenClientException();
        }

        // parse date
        Date date;
        try {
            if (dateStr == null) {
                date = new Date();
            } else {
                date = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
            }
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid date format").build();
        }

        // get logs
        AuditLogDao auditLogDao = new AuditLogDao();
        List<AuditLogDto> logs = auditLogDao.findByDay(date);

        // build JSON response
        JsonArrayBuilder logsJson = Json.createArrayBuilder();
        for (AuditLogDto log : logs) {
            logsJson.add(Json.createObjectBuilder()
                    .add("id", log.getId())
                    .add("username", log.getUsername())
                    .add("target", log.getEntityId())
                    .add("class", log.getEntityClass())
                    .add("type", log.getType().name())
                    .add("message", JsonUtil.nullable(log.getMessage()))
                    .add("create_date", log.getCreateTimestamp()));
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("logs", logsJson)
                .add("total", logs.size());

        return Response.ok().entity(response.build()).build();
    }
}
