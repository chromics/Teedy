'use strict';

/**
 * Admin dashboard controller.
 */
angular.module('docs').controller('AdminDashboard', function($scope, Restangular, $filter, $timeout) {
  $scope.date = new Date();
  $scope.logs = [];
  $scope.expandedUsers = {}; // Track which users are expanded
  $scope.timeline = null;

  const colorMap = {
    View: '#4285F4',
    Edit: '#0F9D58',
    Comment: '#F4B400'
  };

  function groupLogsByUser(logs) {
    const grouped = {};
    logs.forEach(log => {
      if (!grouped[log.username]) {
        grouped[log.username] = [];
      }
      grouped[log.username].push(log);
    });
    return grouped;
  }

  $scope.toggleExpand = function(username) {
    $scope.expandedUsers[username] = !$scope.expandedUsers[username];
    renderTimeline($scope.logs);
  };

  function fixTimelineRowHeights() {
    const groupEls = document.querySelectorAll('.vis-group');
    groupEls.forEach(el => {
      el.style.height = '40px';
      el.style.minHeight = '40px';
      el.style.lineHeight = '40px';
      el.style.padding = '0';
      el.style.borderBottom = '1px solid #ccc';
    });

    const labelEls = document.querySelectorAll('.vis-label');
    labelEls.forEach(el => {
      el.style.height = '40px';
      el.style.lineHeight = '40px';
      el.style.padding = '0 5px';
      el.style.fontSize = '12px';
    });

    const itemEls = document.querySelectorAll('.vis-item');
    itemEls.forEach(el => {
      el.style.height = '14px';
      el.style.lineHeight = '14px';
      el.style.fontSize = '11px';
      el.style.marginTop = '2px';
      el.style.marginBottom = '2px';
      el.style.padding = '0 2px';
    });
  }

  function renderTimeline(logs) {
    const groups = [];
    const items = [];
    const groupIdMap = new Map();
    let nextGroupId = 1;

    logs.forEach(log => {
      const username = log.username;
      const doc = log.target || 'Unknown';
      const expanded = $scope.expandedUsers[username];
      const groupKey = username;

      if (!groupIdMap.has(groupKey)) {
        const groupId = nextGroupId++;
        groupIdMap.set(groupKey, groupId);

        groups.push({
          id: groupId,
          content: expanded ? `↳ ${doc}` : username
        });
      }

      items.push({
        id: log.id,
        group: groupIdMap.get(groupKey),
        content: `<span style="color:${colorMap[log.type] || '#000'}">${log.type}</span>`,
        start: new Date(log.create_date),
        title: `${log.username} ${log.type} ${log.class} ${log.target} - ${log.message}`,
        style: `background-color: ${colorMap[log.type] || '#ccc'};`
      });
    });

    console.log('Rendering timeline with logs:', logs);
    const container = document.getElementById('timeline');
    const visCenter = container.querySelector('.vis-panel.vis-center');
        const groupLabels = container.querySelectorAll('.vis-label.vis-group-level-0');
        const rowHeight = 40;
        if (visCenter && groupLabels.length) {
          visCenter.style.height = (groupLabels.length * rowHeight) + 'px';
        }

    // this fixes the extra timeline - dont touch!
    if (container) container.innerHTML = '';
    console.log('Timeline container:', container);

    if (!container) {
      console.error('Timeline container not found!');
      return;
    }

    if ($scope.timeline) {
      $scope.timeline.destroy();
      $scope.timeline = null;
    }

    const startOfDay = new Date($scope.date);
    startOfDay.setHours(0, 0, 0, 0);
    const endOfDay = new Date($scope.date);
    endOfDay.setHours(23, 59, 59, 999);

    const options = {
      groupOrder: 'content',
      selectable: false,
      tooltip: { followMouse: true },
      stack: false,
      orientation: {
        axis: 'top',
        item: 'bottom'
      },
      showCurrentTime: false,
      zoomMin: 1000 * 60 * 60 * 24,
      zoomMax: 1000 * 60 * 60 * 24,
      min: startOfDay,
      max: endOfDay,
      margin: {
        item: 2,
        axis: 5
      },
      groupHeightMode: 'fixed',
      height: '400px'
    };

    $scope.timeline = new vis.Timeline(container, new vis.DataSet(items), new vis.DataSet(groups), options);

    // Fix heights after a small delay to ensure DOM elements exist
    $timeout(() => {
      console.log('Running $timeout fixTimelineRowHeights');
      fixTimelineRowHeights();

      const rowHeight = 40;

        // Set explicit heights and remove extra padding/margin for all rows in left and center panels
        document.querySelectorAll('.vis-panel.vis-left .vis-group').forEach(el => {
          el.style.height = rowHeight + 'px';
          el.style.lineHeight = rowHeight + 'px';
          el.style.minHeight = rowHeight + 'px';
          el.style.padding = '0';
          el.style.margin = '0';
        });

        document.querySelectorAll('.vis-panel.vis-center .vis-group').forEach(el => {
          el.style.height = rowHeight + 'px';
          el.style.lineHeight = rowHeight + 'px';
          el.style.minHeight = rowHeight + 'px';
          el.style.padding = '0';
          el.style.margin = '0';
        });

        // Calculate total height of all groups and apply to panels
        const groupCount = document.querySelectorAll('.vis-group').length;
        const totalHeight = groupCount * rowHeight;

        const visLeft = document.querySelector('.vis-panel.vis-left');
        const visCenter = document.querySelector('.vis-panel.vis-center');

        if (visLeft) visLeft.style.height = totalHeight + 'px';
        if (visCenter) visCenter.style.height = totalHeight + 'px';

        // Sync vertical scrolling between left and center panels
        if (visLeft && visCenter) {
          visCenter.addEventListener('scroll', () => {
            visLeft.scrollTop = visCenter.scrollTop;
          });
        }

    }, 100);
  }

  $scope.fetchLogs = function() {
    const dateStr = $filter('date')($scope.date, 'yyyy-MM-dd');
    console.log('Fetching logs for date:', dateStr);
    Restangular.one('auditlog/admindashboard').get({ date: dateStr }).then(function(response) {
      console.log('API response:', response);
      $scope.logs = response.logs;

      // Group logs by user
      $scope.userGroupedLogs = groupLogsByUser($scope.logs);

      // Clear expand states if new day
      $scope.expandedUsers = {};
      $timeout(() => renderTimeline($scope.logs), 0);
    }).catch(function(err) {
      console.error('Error fetching logs:', err);
    });
  };

  $scope.previousDay = function() {
    $scope.date.setDate($scope.date.getDate() - 1);
    $scope.fetchLogs();
  };

  $scope.nextDay = function() {
    $scope.date.setDate($scope.date.getDate() + 1);
    $scope.fetchLogs();
  };

  // Initial fetch
  $scope.fetchLogs();
});

/**
 * Filter to extract unique items by key
 */
angular.module('docs').filter('unique', function() {
  return function(items, key) {
    const seen = new Set();
    return items.filter(item => {
      const val = item[key];
      if (seen.has(val)) return false;
      seen.add(val);
      return true;
    });
  };
});
