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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
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
    float alpha = Double.valueOf(CollectionUtil.getDouble("alpha", optionsMap, 1)).floatValue();
    float rotation =
        Double.valueOf(CollectionUtil.getDouble("rotation", optionsMap, 0)).floatValue();
    boolean draggable = CollectionUtil.getBool("draggable", optionsMap, false);
    boolean flat = CollectionUtil.getBool("flat", optionsMap, false);
    boolean visible = CollectionUtil.getBool("visible", optionsMap, true);

    MarkerOptions options = new MarkerOptions();
    if (imagePath != null && !imagePath.isEmpty()) {
      BitmapDescriptor icon = BitmapDescriptorFactory.fromAsset(imagePath);
      options.icon(icon);
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
        }
      });

      animator.start();
    });
  }


  public void removeMarker(String id) {
    UiThreadUtil.runOnUiThread(
        () -> {
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

  /**
   * Adds a text marker on the map using a custom bitmap with text and background.
   *
   * @param optionsMap Configuration map containing text, position, styling options
   * @return The created Marker containing the text with background
   */
  public Marker addTextMarker(Map<String, Object> optionsMap) {
    if (mGoogleMap == null) {
      return null;
    }

    // Extract text marker parameters
    String text = CollectionUtil.getString("text", optionsMap);
    if (text == null || text.isEmpty()) {
      return null; // Text is required
    }

    // Position parameters
    LatLng position = ObjectTranslationUtil.getLatLngFromMap((Map<String, Object>) optionsMap.get("position"));
    if (position == null) {
      return null; // Position is required
    }

    // Styling parameters with defaults
    float fontSize = (float) CollectionUtil.getDouble("fontSize", optionsMap, 14.0);
    String fontColor = CollectionUtil.getString("fontColor", optionsMap);
    int textColor = fontColor != null ? Color.parseColor(fontColor) : Color.BLACK;
    
    String backgroundColor = CollectionUtil.getString("backgroundColor", optionsMap);
    int bgColor = backgroundColor != null ? Color.parseColor(backgroundColor) : Color.WHITE;
    
    float padding = (float) CollectionUtil.getDouble("padding", optionsMap, 8.0);
    float cornerRadius = (float) CollectionUtil.getDouble("cornerRadius", optionsMap, 4.0);
    boolean bold = CollectionUtil.getBool("bold", optionsMap, false);
    boolean visible = CollectionUtil.getBool("visible", optionsMap, true);
    boolean clickable = CollectionUtil.getBool("clickable", optionsMap, true);
    float alpha = (float) CollectionUtil.getDouble("alpha", optionsMap, 1.0);
    float rotation = (float) CollectionUtil.getDouble("rotation", optionsMap, 0.0);
    boolean draggable = CollectionUtil.getBool("draggable", optionsMap, false);
    boolean flat = CollectionUtil.getBool("flat", optionsMap, false);

    // Generate bitmap with text and background
    BitmapDescriptor bitmapDescriptor = createTextBitmap(text, textColor, bgColor, fontSize, padding, cornerRadius, bold);
    if (bitmapDescriptor == null) {
      return null;
    }

    // Create marker options with custom text bitmap
    MarkerOptions options = new MarkerOptions();
    options.icon(bitmapDescriptor);
    options.position(position);
    options.visible(visible);
    options.alpha(alpha);
    options.rotation(rotation);
    options.draggable(draggable);
    options.flat(flat);

    // Add optional title and snippet if provided
    String title = CollectionUtil.getString("title", optionsMap);
    String snippet = CollectionUtil.getString("snippet", optionsMap);
    if (title != null) {
      options.title(title);
    }
    if (snippet != null) {
      options.snippet(snippet);
    }

    Marker textMarker = mGoogleMap.addMarker(options);
    markerList.add(textMarker);
    return textMarker;
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
   * Creates a bitmap with text on a background rectangle.
   * 
   * @param text         The text to render
   * @param textColor    The color of the text
   * @param bgColor      The background color
   * @param fontSize     The font size in pixels
   * @param padding      The padding around the text
   * @param cornerRadius The corner radius for rounded corners
   * @param bold         Whether to use bold font
   * @return BitmapDescriptor containing the rendered text with background
   */
  private BitmapDescriptor createTextBitmap(String text, int textColor, int bgColor, 
                                            float fontSize, float padding, float cornerRadius, boolean bold) {
    try {
      // Create paint for text
      Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      textPaint.setColor(textColor);
      textPaint.setTextSize(fontSize);
      if (bold) {
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
      } else {
        textPaint.setTypeface(Typeface.DEFAULT);
      }

      // Create paint for background
      Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      bgPaint.setColor(bgColor);

      // Measure text dimensions
      Rect textBounds = new Rect();
      textPaint.getTextBounds(text, 0, text.length(), textBounds);
      
      int textWidth = textBounds.width();
      int textHeight = textBounds.height();
      
      // Calculate bitmap dimensions with padding
      int bitmapWidth = (int) (textWidth + (padding * 2));
      int bitmapHeight = (int) (textHeight + (padding * 2));
      
      // Create bitmap and canvas
      Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      
      // Draw background rectangle with rounded corners
      if (cornerRadius > 0) {
        canvas.drawRoundRect(0, 0, bitmapWidth, bitmapHeight, cornerRadius, cornerRadius, bgPaint);
      } else {
        canvas.drawRect(0, 0, bitmapWidth, bitmapHeight, bgPaint);
      }
      
      // Draw text centered on the background
      float textX = padding;
      float textY = padding + textHeight; // textHeight to account for baseline
      
      canvas.drawText(text, textX, textY, textPaint);
      
      return BitmapDescriptorFactory.fromBitmap(bitmap);
    } catch (Exception e) {
      // Return null if bitmap creation fails
      return null;
    }
  }

}
