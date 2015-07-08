var map =  (function() {
	"use strict";

	var mapCanvas;

	// google.maps.Marker, google.maps.Map, google.maps.InfoWindow, String -> ()
	function makeInfoWindow(marker, map, infoWindow, content) {
		google.maps.event.addListener(marker, 'click', function() {
			infoWindow.setContent(content);
			infoWindow.open(map, marker);
		});
	}

	// google.maps.Map -> ()
	function setupMarkers(mapObject) {
		$.ajax({
			type: "GET",
			url: "api/events",
			datatype: "json",
			success: function(response) {
				var infoWindow = new google.maps.InfoWindow();
				google.maps.event.addListener(infoWindow, 'domready', function() {
					$("a[id^=markerEvent]").click(function (e) {
						var id = document.getElementById(e.target.id).dataset.eventid;
						ViewEventHandler.viewEvent(id);
					});
				});
				for (var i = 0; i < response.length; i++) {
					var event = {
						id: response[i].eventsid,
						markerid: "markerEvent" + i,
						title: response[i].title,
						attending: response[i].attending,
						latitude: response[i].latitude,
						longitude: response[i].longitude
					};

					var eventMarker = new google.maps.Marker({
						position: new google.maps.LatLng(event.latitude, event.longitude),
						map: mapObject
					});

					var template = $('#marker-template').html();
					var rendered = Mustache.render(template, event);
					makeInfoWindow(eventMarker, mapObject, infoWindow, rendered);
				}
			}
		});
	}

	// () -> ()
	// Gets the user location and initializes the map to that location
	// Then continues to watch the user location, updating the map
	function initialize() {
		var mapOptions = {
			center: new google.maps.LatLng(40.417, -3.702),
			zoom: 12,
			disableDefaultUI: true
		};

		mapCanvas = new google.maps.Map(document.getElementById('map-canvas'), mapOptions);

		setupMarkers(mapCanvas);

		google.maps.event.addListener(mapCanvas, 'rightclick', function(event) {
			CreateEventHandler.setupPane(event);
		});


		if (navigator.geolocation) {
			navigator.geolocation.watchPosition(function (position) {
				var location = new google.maps.LatLng(position.coords.latitude, position.coords.longitude);
				mapCanvas.panTo(location);
			});
		}
	}

	return {
		initialize: initialize,
		reload: function() {
			setupMarkers(mapCanvas)
		}
	};
}());

google.maps.event.addDomListener(window, 'load', map.initialize);
