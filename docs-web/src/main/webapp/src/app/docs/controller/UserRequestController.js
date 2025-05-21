angular.module('docs').controller('UserRequestController', ['$scope', 'Restangular', '$translate', '$dialog', '$uibModalInstance', 'UserRequest',
  function($scope, Restangular, $translate, $dialog, $uibModalInstance, UserRequest) {

    $scope.request = {
      username: '',
      email: '',
      password: ''
    };

    $scope.status = '';

    $scope.submitRequest = function() {
      if (!$scope.request.username || !$scope.request.email || !$scope.request.password) {
        $scope.status = $translate.instant('userrequest.validation_error');
        $dialog.error($scope.status);
        return;
      }

      // Add client-side validation for password length
      if ($scope.request.password.length < 8) {
        $scope.status = $translate.instant('userrequest.password_too_short');
        $dialog.error($scope.status);
        return;
      }

      if ($scope.request.password.length > 50) {
        $scope.status = $translate.instant('userrequest.password_too_long');
        $dialog.error($scope.status);
        return;
      }

      console.log('Submitting request:', $scope.request);

      UserRequest.createRequest($scope.request).then(function(response) {
        console.log('Success response:', response);
        $scope.status = $translate.instant('userrequest.request_success');
        $dialog.success($scope.status);
        $uibModalInstance.close();
      }, function(response) {
        console.error('Error response:', response);
        console.error('Error data:', JSON.stringify(response.data));

        // Show specific error message based on the error type
        if (response.data && response.data.type === 'AlreadyExistingEmail') {
          $scope.status = $translate.instant('userrequest.email_exists');
        } else if (response.data && response.data.type === 'AlreadyExistingUsername') {
          $scope.status = $translate.instant('userrequest.username_exists');
        } else if (response.data && response.data.type === 'ValidationError' && response.data.message) {
          // Use the specific validation error message from the server
          $scope.status = response.data.message;
        } else {
          // Fallback to generic error message
          $scope.status = $translate.instant('userrequest.request_error');
        }

        // Show the specific error message in the dialog
        $dialog.error($scope.status);
      });
    };

    $scope.cancel = function() {
      $uibModalInstance.dismiss('cancel');  // CANCEL button closes modal
    };
  }
]);