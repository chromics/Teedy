angular.module('docs')
  .controller('SettingsUserRequest', ['$scope', 'UserRequest', '$translate', '$dialog', function($scope, UserRequest, $translate, $dialog) {
    $scope.requests = null;

    $scope.loadRequests = function() {
      UserRequest.getPendingRequests()
        .then(function(response) {
          const requests = Array.isArray(response.requests) ? response.requests : [];
          $scope.requests = requests.sort((a, b) => {
            const dateA = new Date(a.create_date);
            const dateB = new Date(b.create_date);
            return isNaN(dateB) - isNaN(dateA) || dateB - dateA;
          });
        })
        .catch(function(error) {
          $scope.requests = [];
          $scope.errorMessage = $translate.instant('settings.userrequest.error_load');
          console.error('Error loading user requests:', error);
        });
    };

    $scope.loadRequests();

    $scope.approve = function(request) {
      var title = $translate.instant('settings.userrequest.approve_title');
      var msg = $translate.instant('settings.userrequest.approve_message', { username: request.username });
      var btns = [
        { result: 'cancel', label: $translate.instant('cancel') },
        { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-primary' }
      ];

      $dialog.messageBox(title, msg, btns, function(result) {
        if (result === 'ok') {
          UserRequest.approve(request.id)
            .then(function() {
              $scope.loadRequests();
            })
            .catch(function(error) {
              $scope.errorMessage = $translate.instant('settings.userrequest.error_approve');
              console.error('Error approving request:', error);
            });
        }
      });
    };

    $scope.reject = function(request) {
      var title = $translate.instant('settings.userrequest.reject_title');
      var msg = $translate.instant('settings.userrequest.reject_message', { username: request.username });
      var btns = [
        { result: 'cancel', label: $translate.instant('cancel') },
        { result: 'ok', label: $translate.instant('ok'), cssClass: 'btn-danger' }
      ];

      $dialog.messageBox(title, msg, btns, function(result) {
        if (result === 'ok') {
          UserRequest.reject(request.id)
            .then(function() {
              $scope.loadRequests();
            })
            .catch(function(error) {
              $scope.errorMessage = $translate.instant('settings.userrequest.error_reject');
              console.error('Error rejecting request:', error);
            });
        }
      });
    };
  }]);