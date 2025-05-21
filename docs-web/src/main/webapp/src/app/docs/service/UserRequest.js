'use strict';

/**
 * User Request service.
 */
angular.module('docs').factory('UserRequest', function(Restangular) {
    return {
        /**
         * Create a new user request.
         */
        createRequest: function(userData) {
            console.log('UserRequest.createRequest called with:', userData);
            return Restangular.one('userrequest').customPUT(userData);
        },

        /**
         * Returns all pending user requests.
         */
        getPendingRequests: function() {
            return Restangular.one('userrequest').get();
        },

        /**
         * Approve a user request.
         */
        approve: function(id) {
            return Restangular.one('userrequest', id).one('approve').put();
        },

        /**
         * Reject a user request.
         */
        reject: function(id) {
            return Restangular.one('userrequest', id).one('reject').put();
        }
    };
});