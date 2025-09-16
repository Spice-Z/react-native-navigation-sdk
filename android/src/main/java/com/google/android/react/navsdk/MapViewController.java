/**
 * Copyright 2024 Google LLC
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.react.navsdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import androidx.core.util.Supplier;
import com.facebook.react.bridge.UiThreadUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;

public class MapViewController {
  private GoogleMap mGoogleMap;
  private Supplier<Activity> activitySupplier;
  private INavigationViewCallback mNavigationViewCallback;
  private final List<Marker> markerList = new ArrayList<>();
  private final List<Polyline> polylineList = new ArrayList<>();
  private final List<Polygon> polygonList = new ArrayList<>();
  private final List<GroundOverlay> groundOverlayList = new ArrayList<>();
  private final List<Circle> circleList = new ArrayList<>();
  private final HashMap<String, ValueAnimator> markerAnimators = new HashMap<>(); // Store animators for cleanup
  private final HashMap<String, Circle> markerPulseCircles = new HashMap<>(); // Store pulse circles for cleanup
  private String style = "";

  public void initialize(GoogleMap googleMap, Supplier<Activity> activitySupplier) {
    this.mGoogleMap = googleMap;
    this.activitySupplier = activitySupplier;
  }

  public void setupMapListeners(INavigationViewCallback navigationViewCallback) {
    this.mNavigationViewCallback = navigationViewCallback;
    if (mGoogleMap == null || mNavigationViewCallback == null) return;

    mGoogleMap.setOnMarkerClickListener(
        marker -> {
          mNavigationViewCallback.onMarkerClick(marker);
          return false;
        });

    mGoogleMap.setOnPolylineClickListener(
        polyline -> mNavigationViewCallback.onPolylineClick(polyline));
    mGoogleMap.setOnPolygonClickListener(
        polygon -> mNavigationViewCallback.onPolygonClick(polygon));
    mGoogleMap.setOnCircleClickListener(circle -> mNavigationViewCallback.onCircleClick(circle));
    mGoogleMap.setOnGroundOverlayClickListener(
        groundOverlay -> mNavigationViewCallback.onGroundOverlayClick(groundOverlay));
    mGoogleMap.setOnInfoWindowClickListener(
        marker -> mNavigationViewCallback.onMarkerInfoWindowTapped(marker));
    mGoogleMap.setOnMapClickListener(latLng -> mNavigationViewCallback.onMapClick(latLng));
  }

  public GoogleMap getGoogleMap() {
    return mGoogleMap;
  }

  public Circle addCircle(Map<String, Object> optionsMap) {
    if (mGoogleMap == null) {
      return null;
    }

    CircleOptions options = new CircleOptions();

    float strokeWidth =
        Double.valueOf(CollectionUtil.getDouble("strokeWidth", optionsMap, 0)).floatValue();
    options.strokeWidth(strokeWidth);

    double radius = CollectionUtil.getDouble("radius", optionsMap, 0.0);
    options.radius(radius);

    boolean visible = CollectionUtil.getBool("visible", optionsMap, true);
    options.visible(visible);

    options.center(
        ObjectTranslationUtil.getLatLngFromMap((Map<String, Object>) optionsMap.get("center")));

    boolean clickable = CollectionUtil.getBool("clickable", optionsMap, false);
    options.clickable(clickable);

    String strokeColor = CollectionUtil.getString("strokeColor", optionsMap);
    if (strokeColor != null) {
      options.strokeColor(Color.parseColor(strokeColor));
    }

    String fillColor = CollectionUtil.getString("fillColor", optionsMap);
    if (fillColor != null) {
      options.fillColor(Color.parseColor(fillColor));
    }

    Circle circle = mGoogleMap.addCircle(options);
    circleList.add(circle);

    return circle;
  }

  public Marker addMarker(Map<String, Object> optionsMap) {
    if (mGoogleMap == null) {
      return null;
    }

    String imagePath = CollectionUtil.getString("imgPath", optionsMap);
    String title = CollectionUtil.getString("title", optionsMap);
    String snippet = CollectionUtil.getString("snippet", optionsMap);
    String color = CollectionUtil.getString("color", optionsMap);
    float alpha = Double.valueOf(CollectionUtil.getDouble("alpha", optionsMap, 1)).floatValue();
    float rotation =
        Double.valueOf(CollectionUtil.getDouble("rotation", optionsMap, 0)).floatValue();
    boolean draggable = CollectionUtil.getBool("draggable", optionsMap, false);
    boolean flat = CollectionUtil.getBool("flat", optionsMap, false);
    boolean visible = CollectionUtil.getBool("visible", optionsMap, true);
    boolean animateOnAdd = CollectionUtil.getBool("animateOnAdd", optionsMap, true); // Default to true for pulse

    MarkerOptions options = new MarkerOptions();
    if (imagePath != null && !imagePath.isEmpty()) {
      BitmapDescriptor icon = BitmapDescriptorFactory.fromAsset(imagePath);
      options.icon(icon);
    } else {
      // Only apply color if no custom image is provided
      float markerHue = getMarkerHueFromColor(color);
      options.icon(BitmapDescriptorFactory.defaultMarker(markerHue));
    }

    options.position(
        ObjectTranslationUtil.getLatLngFromMap((Map<String, Object>) optionsMap.get("position")));

    if (title != null) {
      options.title(title);
    }

    if (snippet != null) {
      options.snippet(snippet);
    }

    options.flat(flat);
    options.alpha(alpha);
    options.rotation(rotation);
    options.draggable(draggable);
    options.visible(visible);

    Marker marker = mGoogleMap.addMarker(options);
    // Apply pulse animation to the newly added marker if enabled
    if (marker != null && animateOnAdd) {
      animateMarkerPulse(marker, color);
    }

    markerList.add(marker);

    return marker;
  }

  public Polyline addPolyline(Map<String, Object> optionsMap) {
    if (mGoogleMap == null) {
      return null;
    }

    float width = Double.valueOf(CollectionUtil.getDouble("width", optionsMap, 0)).floatValue();
    boolean clickable = CollectionUtil.getBool("clickable", optionsMap, false);
    boolean visible = CollectionUtil.getBool("visible", optionsMap, true);

    ArrayList latLngArr = (ArrayList) optionsMap.get("points");

    if (latLngArr == null) {
      return null;
    }

    PolylineOptions options = new PolylineOptions();
    for (int i = 0; i < latLngArr.size(); i++) {
      Map<String, Object> latLngMap = (Map<String, Object>) latLngArr.get(i);
      LatLng latLng = createLatLng(latLngMap);
      options.add(latLng);
    }

    String color = CollectionUtil.getString("color", optionsMap);
    if (color != null) {
      options.color(Color.parseColor(color));
    }

    options.width(width);
    options.clickable(clickable);
    options.visible(visible);

    Polyline polyline = mGoogleMap.addPolyline(options);
    polylineList.add(polyline);

    return polyline;
  }

  public Polygon addPolygon(Map<String, Object> optionsMap) {
    if (mGoogleMap == null) {
      return null;
    }

    String strokeColor = CollectionUtil.getString("strokeColor", optionsMap);
    String fillColor = CollectionUtil.getString("fillColor", optionsMap);
    float strokeWidth =
        Double.valueOf(CollectionUtil.getDouble("strokeWidth", optionsMap, 0)).floatValue();
    boolean clickable = CollectionUtil.getBool("clickable", optionsMap, false);
    boolean geodesic = CollectionUtil.getBool("geodesic", optionsMap, false);
    boolean visible = CollectionUtil.getBool("visible", optionsMap, true);

    ArrayList latLngArr = (ArrayList) optionsMap.get("points");

    PolygonOptions options = new PolygonOptions();
    for (int i = 0; i < latLngArr.size(); i++) {
      Map<String, Object> latLngMap = (Map<String, Object>) latLngArr.get(i);
      LatLng latLng = createLatLng(latLngMap);
      options.add(latLng);
    }

    ArrayList holesArr = (ArrayList) optionsMap.get("holes");

    for (int i = 0; i < holesArr.size(); i++) {
      ArrayList arr = (ArrayList) holesArr.get(i);

      List<LatLng> listHoles = new ArrayList<>();

      for (int j = 0; j < arr.size(); j++) {
        Map<String, Object> latLngMap = (Map<String, Object>) arr.get(j);
        LatLng latLng = createLatLng(latLngMap);

        listHoles.add(latLng);
      }

      options.addHole(listHoles);
    }

    if (fillColor != null) {
      options.fillColor(Color.parseColor(fillColor));
    }

    if (strokeColor != null) {
      options.strokeColor(Color.parseColor(strokeColor));
    }

    options.strokeWidth(strokeWidth);
    options.visible(visible);
    options.geodesic(geodesic);
    options.clickable(clickable);

    Polygon polygon = mGoogleMap.addPolygon(options);
    polygonList.add(polygon);

    return polygon;
  }

  public GroundOverlay addGroundOverlay(Map<String, Object> map) {
    if (mGoogleMap == null) {
      return null;
    }

    String imagePath = CollectionUtil.getString("imgPath", map);
    float width = Double.valueOf(CollectionUtil.getDouble("width", map, 0)).floatValue();
    float height = Double.valueOf(CollectionUtil.getDouble("height", map, 0)).floatValue();
    float transparency =
        Double.valueOf(CollectionUtil.getDouble("transparency", map, 0)).floatValue();
    boolean clickable = CollectionUtil.getBool("clickable", map, false);
    boolean visible = CollectionUtil.getBool("visible", map, true);

    Double lat = null;
    Double lng = null;
    if (map.containsKey("location")) {
      Map<String, Object> latlng = (Map<String, Object>) map.get("location");
      if (latlng.get(Constants.LAT_FIELD_KEY) != null)
        lat = Double.parseDouble(latlng.get(Constants.LAT_FIELD_KEY).toString());
      if (latlng.get(Constants.LNG_FIELD_KEY) != null)
        lng = Double.parseDouble(latlng.get(Constants.LNG_FIELD_KEY).toString());
    }

    GroundOverlayOptions options = new GroundOverlayOptions();
    if (imagePath != null && !imagePath.isEmpty()) {
      BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromAsset(imagePath);
      options.image(bitmapDescriptor);
    }
    options.position(new LatLng(lat, lng), width, height);
    options.transparency(transparency);
    options.clickable(clickable);
    options.visible(visible);
    GroundOverlay groundOverlay = mGoogleMap.addGroundOverlay(options);
    groundOverlayList.add(groundOverlay);
    return groundOverlay;
  }

  /**
   * Animates a marker to a new position with smooth movement.
   * Also moves the associated pulse circle if it exists.
   * 
   * @param markerId    The ID of the marker to move
   * @param newPosition The new position to move the marker to
   * @param duration    The duration of the animation in milliseconds
   */
  public void animateMarkerToPosition(String markerId, Map<String, Object> newPosition, int duration) {
    if (mGoogleMap == null) {
      return;
    }

    UiThreadUtil.runOnUiThread(() -> {
      // Find the marker
      Marker markerToMove = null;
      for (Marker marker : markerList) {
        if (marker.getId().equals(markerId)) {
          markerToMove = marker;
          break;
        }
      }

      if (markerToMove == null) {
        return; // Marker not found
      }

      final Marker finalMarker = markerToMove;
      final LatLng startPosition = finalMarker.getPosition();
      final LatLng endPosition = ObjectTranslationUtil.getLatLngFromMap(newPosition);

      // Get the pulse circle if it exists
      final Circle pulseCircle = markerPulseCircles.get(markerId);

      // Create smooth animation
      ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
      animator.setDuration(duration > 0 ? duration : 1000); // Default 1 second
      animator.setInterpolator(new AccelerateDecelerateInterpolator());

      animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
          float progress = (float) animation.getAnimatedValue();

          // Calculate intermediate position using linear interpolation
          double lat = startPosition.latitude + (endPosition.latitude - startPosition.latitude) * progress;
          double lng = startPosition.longitude + (endPosition.longitude - startPosition.longitude) * progress;
          LatLng intermediatePosition = new LatLng(lat, lng);

          // Update marker position
          finalMarker.setPosition(intermediatePosition);

          // Update pulse circle position if it exists
          if (pulseCircle != null) {
            pulseCircle.setCenter(intermediatePosition);
          }
        }
      });

      animator.start();
    });
  }

  /**
   * Immediately moves a marker to a new position without animation.
   * Also moves the associated pulse circle if it exists.
   * 
   * @param markerId    The ID of the marker to move
   * @param newPosition The new position to move the marker to
   */
  public void moveMarker(String markerId, Map<String, Object> newPosition) {
    if (mGoogleMap == null) {
      return;
    }

    UiThreadUtil.runOnUiThread(() -> {
      // Find the marker
      Marker markerToMove = null;
      for (Marker marker : markerList) {
        if (marker.getId().equals(markerId)) {
          markerToMove = marker;
          break;
        }
      }

      if (markerToMove == null) {
        return; // Marker not found
      }

      LatLng newLatLng = ObjectTranslationUtil.getLatLngFromMap(newPosition);

      // Update marker position
      markerToMove.setPosition(newLatLng);

      // Update pulse circle position if it exists
      Circle pulseCircle = markerPulseCircles.get(markerId);
      if (pulseCircle != null) {
        pulseCircle.setCenter(newLatLng);
      }
    });
  }

  public void removeMarker(String id) {
    UiThreadUtil.runOnUiThread(
        () -> {
          // Stop any animations for this marker
          stopMarkerAnimation(id);
          for (Marker m : markerList) {
            if (m.getId().equals(id)) {
              m.remove();
              markerList.remove(m);
              return;
            }
          }
        });
  }

  public void removePolyline(String id) {
    for (Polyline p : polylineList) {
      if (p.getId().equals(id)) {
        p.remove();
        polylineList.remove(p);
        return;
      }
    }
  }

  public void removePolygon(String id) {
    for (Polygon p : polygonList) {
      if (p.getId().equals(id)) {
        p.remove();
        polygonList.remove(p);
        return;
      }
    }
  }

  public void removeCircle(String id) {
    for (Circle c : circleList) {
      if (c.getId().equals(id)) {
        c.remove();
        circleList.remove(c);
        return;
      }
    }
  }

  public void removeGroundOverlay(String id) {
    for (GroundOverlay g : groundOverlayList) {
      if (g.getId().equals(id)) {
        g.remove();
        groundOverlayList.remove(g);
        return;
      }
    }
  }

  public void setMapStyle(String url) {
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              try {
                style = fetchJsonFromUrl(url);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }

              Activity activity = activitySupplier.get();
              if (activity != null) {
                activity.runOnUiThread(
                    () -> {
                      MapStyleOptions options = new MapStyleOptions(style);
                      mGoogleMap.setMapStyle(options);
                    });
              }
            });
  }

  /** Moves the position of the camera to hover over Melbourne. */
  public void moveCamera(Map<String, Object> map) {
    LatLng latLng = ObjectTranslationUtil.getLatLngFromMap((Map<String, Object>) map.get("target"));

    float zoom = (float) CollectionUtil.getDouble("zoom", map, 0);
    float tilt = (float) CollectionUtil.getDouble("tilt", map, 0);
    float bearing = (float) CollectionUtil.getDouble("bearing", map, 0);

    CameraPosition cameraPosition =
        CameraPosition.builder().target(latLng).zoom(zoom).tilt(tilt).bearing(bearing).build();

    mGoogleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
  }

  public void animateCamera(Map<String, Object> map) {
    if (mGoogleMap != null) {
      int zoom = CollectionUtil.getInt("zoom", map, 0);
      int tilt = CollectionUtil.getInt("tilt", map, 0);
      int bearing = CollectionUtil.getInt("bearing", map, 0);
      int animationDuration = CollectionUtil.getInt("duration", map, 0);

      CameraPosition cameraPosition =
          new CameraPosition.Builder()
              .target(
                  ObjectTranslationUtil.getLatLngFromMap(
                      (Map<String, Object>) map.get("target"))) // Set the target location
              .zoom(zoom) // Set the desired zoom level
              .tilt(tilt) // Set the desired tilt angle (0 for straight down, 90 for straight up)
              .bearing(bearing) // Set the desired bearing (rotation angle in degrees)
              .build();

      mGoogleMap.animateCamera(
          CameraUpdateFactory.newCameraPosition(cameraPosition), animationDuration, null);
    }
  }

  public void setZoomLevel(int level) {
    if (mGoogleMap != null) {
      mGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(level));
    }
  }

  public void setIndoorEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.setIndoorEnabled(isOn);
    }
  }

  public void setTrafficEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.setTrafficEnabled(isOn);
    }
  }

  public void setCompassEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setCompassEnabled(isOn);
    }
  }

  public void setRotateGesturesEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setRotateGesturesEnabled(isOn);
    }
  }

  public void setScrollGesturesEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setScrollGesturesEnabled(isOn);
    }
  }

  public void setScrollGesturesEnabledDuringRotateOrZoom(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setScrollGesturesEnabledDuringRotateOrZoom(isOn);
    }
  }

  public void setTiltGesturesEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setTiltGesturesEnabled(isOn);
    }
  }

  public void setZoomControlsEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setZoomControlsEnabled(isOn);
    }
  }

  public void setZoomGesturesEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setZoomGesturesEnabled(isOn);
    }
  }

  public void setBuildingsEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.setBuildingsEnabled(isOn);
    }
  }

  @SuppressLint("MissingPermission")
  public void setMyLocationEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.setMyLocationEnabled(isOn);
    }
  }

  public void setMapToolbarEnabled(boolean isOn) {
    if (mGoogleMap != null) {
      mGoogleMap.getUiSettings().setMapToolbarEnabled(isOn);
    }
  }

  /** Toggles whether the location marker is enabled. */
  public void setMyLocationButtonEnabled(boolean isOn) {
    if (mGoogleMap == null) {
      return;
    }

    UiThreadUtil.runOnUiThread(
        () -> {
          mGoogleMap.getUiSettings().setMyLocationButtonEnabled(isOn);
        });
  }

  public void setMapType(int jsValue) {
    if (mGoogleMap == null) {
      return;
    }

    mGoogleMap.setMapType(EnumTranslationUtil.getMapTypeFromJsValue(jsValue));
  }

  public void clearMapView() {
    if (mGoogleMap == null) {
      return;
    }

    // Stop all marker animations
    for (ValueAnimator animator : markerAnimators.values()) {
      animator.cancel();
    }
    markerAnimators.clear();

    // Remove all pulse circles
    for (Circle circle : markerPulseCircles.values()) {
      circle.remove();
    }
    markerPulseCircles.clear();

    mGoogleMap.clear();
  }

  public void resetMinMaxZoomLevel() {
    if (mGoogleMap == null) {
      return;
    }

    mGoogleMap.resetMinMaxZoomPreference();
  }

  @SuppressLint("MissingPermission")
  public void setFollowingPerspective(int jsValue) {
    if (mGoogleMap == null) {
      return;
    }

    mGoogleMap.followMyLocation(EnumTranslationUtil.getCameraPerspectiveFromJsValue(jsValue));
  }

  public void setPadding(int top, int left, int bottom, int right) {
    if (mGoogleMap != null) {
      mGoogleMap.setPadding(left, top, right, bottom);
    }
  }

  private String fetchJsonFromUrl(String urlString) throws IOException {
    URL url = new URL(urlString);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");

    int responseCode = connection.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      InputStream inputStream = connection.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder stringBuilder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
      }
      reader.close();
      inputStream.close();
      return stringBuilder.toString();
    } else {
      // Handle error response
      throw new IOException("Error response: " + responseCode);
    }
  }

  private LatLng createLatLng(Map<String, Object> map) {
    Double lat = null;
    Double lng = null;
    if (map.containsKey(Constants.LAT_FIELD_KEY) && map.containsKey(Constants.LNG_FIELD_KEY)) {
      if (map.get(Constants.LAT_FIELD_KEY) != null)
        lat = Double.parseDouble(map.get(Constants.LAT_FIELD_KEY).toString());
      if (map.get(Constants.LNG_FIELD_KEY) != null)
        lng = Double.parseDouble(map.get(Constants.LNG_FIELD_KEY).toString());
    }

    return new LatLng(lat, lng);
  }

  /**
   * Maps color string to BitmapDescriptorFactory HUE constant.
   * 
   * @param color Color name string (e.g., "red", "blue", "green")
   * @return The corresponding HUE value, defaults to HUE_RED if color is null or
   *         unrecognized
   */
  private float getMarkerHueFromColor(String color) {
    if (color == null) {
      return BitmapDescriptorFactory.HUE_RED; // Default color
    }

    switch (color.toLowerCase()) {
      case "azure":
        return BitmapDescriptorFactory.HUE_AZURE;
      case "blue":
        return BitmapDescriptorFactory.HUE_BLUE;
      case "cyan":
        return BitmapDescriptorFactory.HUE_CYAN;
      case "green":
        return BitmapDescriptorFactory.HUE_GREEN;
      case "magenta":
        return BitmapDescriptorFactory.HUE_MAGENTA;
      case "orange":
        return BitmapDescriptorFactory.HUE_ORANGE;
      case "red":
        return BitmapDescriptorFactory.HUE_RED;
      case "rose":
        return BitmapDescriptorFactory.HUE_ROSE;
      case "violet":
        return BitmapDescriptorFactory.HUE_VIOLET;
      case "yellow":
        return BitmapDescriptorFactory.HUE_YELLOW;
      default:
        return BitmapDescriptorFactory.HUE_RED; // Default to red for unrecognized colors
    }
  }

  /**
   * Gets the RGB color values for the pulse circle based on the marker color.
   * Returns an array with [strokeColor, fillColor].
   * 
   * @param markerColor The color name of the marker
   * @return Array containing stroke and fill colors for the pulse circle
   */
  private int[] getPulseColorFromMarkerColor(String markerColor) {
    int strokeColor;
    int fillColor;

    if (markerColor == null) {
      markerColor = "red"; // Default
    }

    // Map marker colors to RGB values for the pulse circle
    switch (markerColor.toLowerCase()) {
      case "azure":
        strokeColor = Color.parseColor("#007FFF"); // Azure blue
        break;
      case "blue":
        strokeColor = Color.parseColor("#4285F4"); // Google blue
        break;
      case "cyan":
        strokeColor = Color.parseColor("#00BCD4"); // Cyan
        break;
      case "green":
        strokeColor = Color.parseColor("#4CAF50"); // Green
        break;
      case "magenta":
        strokeColor = Color.parseColor("#E91E63"); // Magenta
        break;
      case "orange":
        strokeColor = Color.parseColor("#FF9800"); // Orange
        break;
      case "red":
        strokeColor = Color.parseColor("#F44336"); // Red
        break;
      case "rose":
        strokeColor = Color.parseColor("#FF1493"); // Rose/Deep pink
        break;
      case "violet":
        strokeColor = Color.parseColor("#9C27B0"); // Violet/Purple
        break;
      case "yellow":
        strokeColor = Color.parseColor("#FFEB3B"); // Yellow
        break;
      default:
        strokeColor = Color.parseColor("#F44336"); // Default to red
        break;
    }

    // Fill color is the same as stroke but with transparency
    int r = Color.red(strokeColor);
    int g = Color.green(strokeColor);
    int b = Color.blue(strokeColor);
    fillColor = Color.argb(51, r, g, b); // 20% opacity for fill

    return new int[] { strokeColor, fillColor };
  }

  /**
   * Animates a marker with a pulsing circle effect underneath.
   * Creates a circle at the marker's position that pulses in radius and opacity.
   * 
   * @param marker      The marker to animate
   * @param markerColor The color of the marker (used to color the pulse circle)
   */
  private void animateMarkerPulse(final Marker marker, String markerColor) {
    if (marker == null || mGoogleMap == null)
      return;

    final long pulseDuration = 1500; // Duration for one pulse cycle
    final float minRadius = 20f; // Minimum circle radius in meters
    final float maxRadius = 60f; // Maximum circle radius in meters

    // Stop any existing animation for this marker
    stopMarkerAnimation(marker.getId());

    // Determine the pulse circle color based on marker color
    final int[] pulseColors = getPulseColorFromMarkerColor(markerColor);
    final int baseStrokeColor = pulseColors[0];
    final int baseFillColor = pulseColors[1];

    // Create a circle at the marker's position
    CircleOptions circleOptions = new CircleOptions()
        .center(marker.getPosition())
        .radius(minRadius)
        .strokeColor(baseStrokeColor)
        .strokeWidth(3f)
        .fillColor(baseFillColor);

    final Circle pulseCircle = mGoogleMap.addCircle(circleOptions);

    // Store the circle reference for cleanup
    markerPulseCircles.put(marker.getId(), pulseCircle);

    // Create the pulse animation using ValueAnimator
    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(pulseDuration);
    animator.setRepeatCount(ValueAnimator.INFINITE); // Loop infinitely
    animator.setRepeatMode(ValueAnimator.RESTART); // Restart animation from beginning
    animator.setInterpolator(new AccelerateDecelerateInterpolator());

    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        float progress = (float) animation.getAnimatedValue();

        try {
          // Animate radius from min to max
          float currentRadius = minRadius + (maxRadius - minRadius) * progress;
          pulseCircle.setRadius(currentRadius);

          // Animate alpha from 0.8 to 0 (fade out as it expands)
          float alpha = 0.8f * (1f - progress);

          // Extract RGB values from the base colors
          int r = Color.red(baseStrokeColor);
          int g = Color.green(baseStrokeColor);
          int b = Color.blue(baseStrokeColor);

          int strokeColor = Color.argb((int) (alpha * 255), r, g, b);
          int fillColor = Color.argb((int) (alpha * 127), r, g, b); // 50% of stroke alpha for fill

          pulseCircle.setStrokeColor(strokeColor);
          pulseCircle.setFillColor(fillColor);
        } catch (Exception e) {
          // Circle or marker might have been removed, stop the animation
          animation.cancel();
        }
      }
    });

    // Store the animator for later cleanup
    markerAnimators.put(marker.getId(), animator);

    // Start the animation
    animator.start();
  }

  /**
   * Stops the animation for a specific marker and removes its pulse circle.
   * 
   * @param markerId The ID of the marker whose animation should be stopped
   */
  private void stopMarkerAnimation(String markerId) {
    // Stop the animator
    ValueAnimator animator = markerAnimators.get(markerId);
    if (animator != null) {
      animator.cancel();
      markerAnimators.remove(markerId);
    }

    // Remove the pulse circle
    Circle pulseCircle = markerPulseCircles.get(markerId);
    if (pulseCircle != null) {
      pulseCircle.remove();
      markerPulseCircles.remove(markerId);
    }
  }
}
