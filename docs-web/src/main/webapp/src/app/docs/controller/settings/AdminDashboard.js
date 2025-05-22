'use strict';

angular.module('docs').controller('AdminDashboard', function($scope, Restangular, $filter, $timeout) {
  // Initialize scope variables
  $scope.date = $filter('date')(new Date(), 'yyyy-MM-dd'); // Initialize as YYYY-MM-DD string
  $scope.logs = [];
  $scope.selectedGroup = '';
  $scope.availableGroups = [];
  $scope.sortOrder = 'alphabetical';
  $scope.expandedUsers = {};

  // Color mapping for event types
  const colorMap = {
    'Document-CREATE': '#4285F4', // Blue for Document CREATE
    'Document-UPDATE': '#F4B400', // Orange for Document UPDATE
    'Comment-CREATE': '#AB47BC',  // Purple for Comment CREATE
    'Comment-UPDATE': '#AB47BC',   // Purple for Comment UPDATE
    'Document-DELETE': '#FF0000',  // Red
    'Comment-DELETE': '#FF0000'    // Red
  };

  // Helper to ensure valid YYYY-MM-DD format
  function formatDate(date) {
    return $filter('date')(date, 'yyyy-MM-dd');
  }

  // Fetch logs from backend
  $scope.fetchLogs = function() {
    if (!$scope.date || !/^\d{4}-\d{2}-\d{2}$/.test($scope.date)) {
      $scope.date = formatDate(new Date()); // Fallback to today if invalid
    }
    const dateStr = $scope.date;
    let url = `auditlog/admindashboard?date=${dateStr}`;
    if ($scope.selectedGroup) url += `&group=${$scope.selectedGroup}`;

    Restangular.one(url).get().then(function(response) {
      $scope.logs = response.logs || [];
      $scope.availableGroups = [...new Set($scope.logs.map(log => log.group || 'Ungrouped'))].sort();
      $timeout(renderTimeline, 0);
    }).catch(function(err) {
      console.error('Error fetching logs:', err);
    });
  };

  // Date navigation
  $scope.previousDay = function() {
    const currentDate = new Date($scope.date);
    currentDate.setDate(currentDate.getDate() - 1);
    $timeout(function() {
      $scope.date = formatDate(currentDate);
      $scope.fetchLogs();
    });
  };

  $scope.nextDay = function() {
    const currentDate = new Date($scope.date);
    currentDate.setDate(currentDate.getDate() + 1);
    $timeout(function() {
      $scope.date = formatDate(currentDate);
      $scope.fetchLogs();
    });
  };

  // Handle calendar picker change
  $scope.onDateChange = function() {
    if ($scope.date && /^\d{4}-\d{2}-\d{2}$/.test($scope.date)) {
      $timeout($scope.fetchLogs);
    } else {
      $scope.date = formatDate(new Date());
      $timeout($scope.fetchLogs);
    }
  };

  // Toggle user expansion
  $scope.toggleExpand = function(username) {
    $scope.expandedUsers[username] = !$scope.expandedUsers[username];
    $timeout(renderTimeline, 0); // Ensure chart updates
  };

  // Format date for tooltips
  function formatDateTime(date) {
    return $filter('date')(date, 'yyyy-MM-dd HH:mm:ss');
  }

  // Prepare data for Gantt chart
  function prepareData() {
    const users = {};
    $scope.logs.forEach(log => {
      const user = log.username;
      if (!users[user]) {
        users[user] = { activities: [], documents: {}, count: 0 };
      }
      const activity = {
        user: user,
        timestamp: new Date(log.create_date),
        action: log.type,
        class: log.class,
        document: log.class === 'Document' ? log.target : `Comment-${log.target}`,
        group: log.group || 'Ungrouped'
      };
      users[user].activities.push(activity);
      users[user].documents[activity.document] = users[user].documents[activity.document] || [];
      users[user].documents[activity.document].push(activity);
      users[user].count++;
    });

    const userData = Object.entries(users).map(([user, data]) => ({
      user,
      activities: data.activities,
      documents: Object.entries(data.documents).map(([doc, acts]) => ({
        document: doc,
        activities: acts
      })).sort((a, b) => b.activities.length - a.activities.length),
      count: data.count
    }));

    if ($scope.sortOrder === 'contribution') {
      userData.sort((a, b) => b.count - a.count || a.user.localeCompare(b.user));
    } else {
      userData.sort((a, b) => a.user.localeCompare(b.user));
    }

    return userData;
  }

  // Word-wrapping function for y-axis labels
  function wrapText(text, width) {
    text.each(function() {
      const text = d3.select(this);
      const words = text.text().split(/\s+/).reverse();
      let word;
      let line = [];
      let lineNumber = 0;
      const lineHeight = 1.2; // ems, for 12px font
      const y = text.attr('y');
      const dy = parseFloat(text.attr('dy'));
      let tspan = text.text(null).append('tspan').attr('x', -10).attr('y', y).attr('dy', dy + 'em');

      while (word = words.pop()) {
        line.push(word);
        tspan.text(line.join(' '));
        if (tspan.node().getComputedTextLength() > width) {
          line.pop();
          tspan.text(line.join(' '));
          line = [word];
          tspan = text.append('tspan')
            .attr('x', -10)
            .attr('y', y)
            .attr('dy', ++lineNumber * lineHeight + dy + 'em')
            .text(word);
        }
      }
    });
  }

  // Render Gantt chart
  function renderTimeline() {
    const container = document.getElementById('timeline');
    if (!container) return;

    d3.select(container).selectAll('*').remove();

    const margin = { top: 70, right: 40, bottom: 0, left: 70 }; // Compact left margin for 60px labels
    const width = container.clientWidth - margin.left - margin.right;
    const height = Math.max(400, prepareData().length * 40) - margin.top - margin.bottom;

    const svg = d3.select(container)
      .append('svg')
      .attr('width', width + margin.left + margin.right)
      .attr('height', height + margin.top + margin.bottom);

    // Clip path to prevent ticks from spilling
    svg.append('defs').append('clipPath')
      .attr('id', 'clip')
      .append('rect')
      .attr('x', 0)
      .attr('y', -margin.top)
      .attr('width', width)
      .attr('height', height + margin.top + margin.bottom);

    const g = svg.append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`)
      .attr('clip-path', 'url(#clip)');

    const userData = prepareData();
    if (userData.length === 0) return;

    // Time scale
    const startOfDay = new Date($scope.date);
    startOfDay.setHours(0, 0, 0, 0);
    const endOfDay = new Date($scope.date);
    endOfDay.setHours(23, 59, 59, 999);

    const xScale = d3.scaleTime()
      .domain([startOfDay, endOfDay])
      .range([0, width])
      .clamp(true);

    // Y-scale domain
    const yDomain = [];
    userData.forEach(d => {
      yDomain.push(d.user);
      if ($scope.expandedUsers[d.user]) {
        d.documents.forEach(doc => yDomain.push(`${d.user}/${d.document}`));
      }
    });

    const yScale = d3.scaleBand()
      .domain(yDomain)
      .range([0, height])
      .padding(0.1);

    // Axes
    const xAxis = d3.axisTop(xScale)
      .tickFormat(d3.timeFormat('%H:%M'))
      .ticks(d3.timeHour.every(1)); // Initial 1-hour intervals

    const yAxis = d3.axisLeft(yScale)
      .tickSize(0);

    const xAxisGroup = svg.append('g')
      .attr('class', 'x-axis')
      .attr('transform', `translate(${margin.left},${margin.top})`)
//      .call(xAxis.ticks(6)); // Limit to ~6 ticks

    const yAxisGroup = svg.append('g')
      .attr('class', 'y-axis')
      .attr('transform', `translate(${margin.left},${margin.top})`)
      .call(yAxis);

    // Wrap y-axis labels to 60px
    yAxisGroup.selectAll('.tick text')
      .call(wrapText, 60);

    // Tooltip
    let tooltip = d3.select('#timeline-tooltip');
    if (tooltip.empty()) {
      tooltip = d3.select('body').append('div')
        .attr('id', 'timeline-tooltip')
        .style('position', 'absolute')
        .style('padding', '6px')
        .style('background', 'rgba(0,0,0,0.7)')
        .style('color', '#fff')
        .style('border-radius', '4px')
        .style('pointer-events', 'none')
        .style('opacity', 0);
    }

    // User groups
    const userGroups = g.selectAll('.user-group')
      .data(userData)
      .enter()
      .append('g')
      .attr('class', 'user-group')
      .attr('transform', d => `translate(0,${yScale(d.user)})`);

    // Dropdown toggle
    userGroups.append('text')
      .attr('x', -10)
      .attr('y', yScale.bandwidth() / 2)
      .attr('dy', '.35em')
      .attr('text-anchor', 'end')
      .text(d => $scope.expandedUsers[d.user] ? '▲' : '▼')
      .style('cursor', 'pointer')
      .on('click', (event, d) => {
        $scope.$apply(() => $scope.toggleExpand(d.user));
      });

    // Event ticks for users (non-expanded)
    userGroups.each(function(d) {
      if ($scope.expandedUsers[d.user]) return;
      const g = d3.select(this);
      g.selectAll('.event-tick')
        .data(d.activities)
        .enter()
        .append('line')
        .attr('class', 'event-tick')
        .attr('x1', d => xScale(d.timestamp))
        .attr('x2', d => xScale(d.timestamp))
        .attr('y1', 0)
        .attr('y2', yScale.bandwidth())
        .attr('stroke', d => colorMap[`${d.class}-${d.action}`] || '#ccc')
        .attr('stroke-width', 2)
        .on('mouseover', function(event, d) {
          tooltip.transition().duration(200).style('opacity', 0.9);
          tooltip.html(`
            <strong>User:</strong> ${d.user}<br/>
            <strong>Document:</strong> ${d.document}<br/>
            <strong>Type:</strong> ${d.action}<br/>
            <strong>Class:</strong> ${d.class}<br/>
            <strong>Time:</strong> ${formatDateTime(d.timestamp)}
          `)
            .style('left', (event.pageX + 10) + 'px')
            .style('top', (event.pageY - 28) + 'px');
        })
        .on('mouseout', () => {
          tooltip.transition().duration(500).style('opacity', 0);
        });
    });

    // Document rows (when expanded)
    userGroups.filter(d => $scope.expandedUsers[d.user])
      .selectAll('.doc-row')
      .data(d => d.documents.map(doc => ({ ...doc, user: d.user })))
      .enter()
      .append('g')
      .attr('class', 'doc-row')
      .attr('transform', d => `translate(0,${yScale(`${d.user}/${d.document}`)})`);
    userGroups.each(function(d) {
      if (!$scope.expandedUsers[d.user]) return;
      const g = d3.select(this);
      g.selectAll('.doc-row')
        .each(function(doc) {
          const docG = d3.select(this);
          // Document label
          docG.append('text')
            .attr('x', 10)
            .attr('y', yScale.bandwidth() / 2)
            .attr('dy', '.35em')
            .text(doc.document);
          // Event ticks
          docG.selectAll('.event-tick')
            .data(doc.activities)
            .enter()
            .append('line')
            .attr('class', 'event-tick')
            .attr('x1', d => xScale(d.timestamp))
            .attr('x2', d => xScale(d.timestamp))
            .attr('y1', 0)
            .attr('y2', yScale.bandwidth())
            .attr('stroke', d => colorMap[`${d.class}-${d.action}`] || '#ccc')
            .attr('stroke-width', 2)
            .on('mouseover', function(event, d) {
              tooltip.transition().duration(200).style('opacity', 0.9);
              tooltip.html(`
                <strong>User:</strong> ${d.user}<br/>
                <strong>Document:</strong> ${d.document}<br/>
                <strong>Type:</strong> ${d.action}<br/>
                <strong>Class:</strong> ${d.class}<br/>
                <strong>Time:</strong> ${formatDateTime(d.timestamp)}
              `)
                .style('left', (event.pageX + 10) + 'px')
                .style('top', (event.pageY - 28) + 'px');
            })
            .on('mouseout', () => {
              tooltip.transition().duration(500).style('opacity', 0);
            });
        });
    });

    // Zoom and pan
    const zoom = d3.zoom()
      .scaleExtent([2, 88]) // Min 5x (~12-hour window), max 24x (~1-hour window)
      .translateExtent([[0, 0], [width, height]]) // Restrict panning to timeline bounds
      .extent([[0, 0], [width, height]])
      .on('zoom', zoomed);

    svg.call(zoom)

    const initialScale = 2;

    // The initial transform will scale by initialScale and translate x so the start of the day stays visible.
    // Since scaling shrinks the domain by initialScale, to show start of the day, no translation needed:
    const initialTransform = d3.zoomIdentity.scale(initialScale).translate(0, 0);

    svg.call(zoom.transform, initialTransform);

    function zoomed(event) {
      const newXScale = event.transform.rescaleX(xScale);

      // Update x-axis with dynamic ticks based on zoom level
      const zoomLevel = event.transform.k;
      let tickInterval;
      if (zoomLevel <= 2) {
        tickInterval = d3.timeHour.every(1); // 1-hour intervals
      } else if (zoomLevel <= 8) {
        tickInterval = d3.timeMinute.every(30); // 30-minute intervals
      } else if (zoomLevel <= 16) {
        tickInterval = d3.timeMinute.every(15); // 15-minute intervals
      } else if (zoomLevel <= 72) {
        tickInterval = d3.timeMinute.every(5); // 5-minute intervals
      } else {
        tickInterval = d3.timeMinute.every(1); // 1-minute intervals
      }
      xAxisGroup.call(xAxis.scale(newXScale).ticks(tickInterval));

      // Update event ticks
      g.selectAll('.event-tick')
        .attr('x1', d => newXScale(d.timestamp))
        .attr('x2', d => newXScale(d.timestamp));
    }
  }

  // Initialize
  $scope.fetchLogs();
});