package org.maplibre.navigation.android.navigation.ui.v5;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.maplibre.navigation.android.navigation.ui.v5.camera.NavigationCamera;
import org.maplibre.navigation.android.navigation.ui.v5.instruction.ImageCreator;
import org.maplibre.navigation.android.navigation.ui.v5.instruction.NavigationAlertView;
import org.maplibre.navigation.android.navigation.ui.v5.map.NavigationMapLibreMap;
import org.maplibre.navigation.android.navigation.ui.v5.map.NavigationMapLibreMapInstanceState;
import org.maplibre.navigation.android.navigation.ui.v5.map.WayNameView;
import org.maplibre.navigation.android.navigation.ui.v5.summary.SummaryBottomSheet;
import org.maplibre.navigation.core.location.Location;
import org.maplibre.navigation.core.models.DirectionsRoute;
import org.maplibre.navigation.core.models.RouteOptions;
import org.maplibre.geojson.Point;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.navigation.android.navigation.ui.v5.instruction.InstructionView;
import org.maplibre.navigation.core.location.replay.ReplayRouteLocationEngine;
import org.maplibre.navigation.core.models.UnitType;
import org.maplibre.navigation.core.navigation.MapLibreNavigation;
import org.maplibre.navigation.core.navigation.MapLibreNavigationOptions;
import org.maplibre.navigation.android.navigation.ui.v5.utils.DistanceFormatter;
import org.maplibre.navigation.android.navigation.ui.v5.utils.LocaleUtils;

/**
 * View that creates the drop-in UI.
 * <p>
 * Once started, this view will check if the {@link Activity} that inflated
 * it was launched with a {@link DirectionsRoute}.
 * <p>
 * Or, if not found, this view will look for a set of {@link Point} coordinates.
 * In the latter case, a new {@link DirectionsRoute} will be retrieved from {@link NavigationRoute}.
 * <p>
 * Once valid data is obtained, this activity will immediately begin navigation
 * with {@link MapLibreNavigation}.
 * <p>
 * If launched with the simulation boolean set to true, a {@link ReplayRouteLocationEngine}
 * will be initialized and begin pushing updates.
 * <p>
 * This activity requires user permissions ACCESS_FINE_LOCATION
 * and ACCESS_COARSE_LOCATION have already been granted.
 * <p>
 * A Mapbox access token must also be set by the developer (to initialize navigation).
 *
 * @since 0.7.0
 */
public class NavigationView extends CoordinatorLayout implements LifecycleOwner, OnMapReadyCallback,
  NavigationContract.View {

  private static final String MAP_INSTANCE_STATE_KEY = "navgation_mapbox_map_instance_state";
  private static final int INVALID_STATE = 0;
  private MapView mapView;
  private InstructionView instructionView;
  private SummaryBottomSheet summaryBottomSheet;
  private BottomSheetBehavior summaryBehavior;
  private ImageButton cancelBtn;
  private RecenterButton recenterBtn;
  private WayNameView wayNameView;
  private ImageButton routeOverviewBtn;

  private NavigationPresenter navigationPresenter;
  private NavigationViewEventDispatcher navigationViewEventDispatcher;
  private NavigationViewModel navigationViewModel;
  private NavigationMapLibreMap navigationMap;
  private OnNavigationReadyCallback onNavigationReadyCallback;
  private NavigationOnCameraTrackingChangedListener onTrackingChangedListener;
  private NavigationMapLibreMapInstanceState mapInstanceState;
  private CameraPosition initialMapCameraPosition;
  private boolean isMapInitialized;
  private boolean isSubscribed;
  private LifecycleRegistry lifecycleRegistry;
  private String lightStyleUrl;
  private String darkStyleUrl;

  public NavigationView(Context context) {
    this(context, null);
  }

  public NavigationView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, -1);
  }

  public NavigationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    ThemeSwitcher.setTheme(context, attrs);
    initializeView();
  }

  /**
   * Uses savedInstanceState as a cue to restore state (if not null).
   *
   * @param savedInstanceState to restore state if not null
   */
  public void onCreate(@Nullable Bundle savedInstanceState) {
    mapView.onCreate(savedInstanceState);
    updatePresenterState(savedInstanceState);
    lifecycleRegistry = new LifecycleRegistry(this);
    lifecycleRegistry.markState(Lifecycle.State.CREATED);
  }

  /**
   * Low memory must be reported so the {@link MapView}
   * can react appropriately.
   */
  public void onLowMemory() {
    mapView.onLowMemory();
  }

  /**
   * If the instruction list is showing and onBackPressed is called,
   * hide the instruction list and do not hide the activity or fragment.
   *
   * @return true if back press handled, false if not
   */
  public boolean onBackPressed() {
    return instructionView.handleBackPressed();
  }

  /**
   * Used to store the bottomsheet state and re-center
   * button visibility.  As well as anything the {@link MapView}
   * needs to store in the bundle.
   *
   * @param outState to store state variables
   */
  public void onSaveInstanceState(Bundle outState) {
    int bottomSheetBehaviorState = summaryBehavior == null ? INVALID_STATE : summaryBehavior.getState();
    boolean isWayNameVisible = wayNameView.getVisibility() == VISIBLE;
    NavigationViewInstanceState navigationViewInstanceState = new NavigationViewInstanceState(
      bottomSheetBehaviorState, recenterBtn.getVisibility(), instructionView.isShowingInstructionList(),
      isWayNameVisible, wayNameView.retrieveWayNameText(), navigationViewModel.isMuted());
    String instanceKey = getContext().getString(R.string.navigation_view_instance_state);
    outState.putParcelable(instanceKey, navigationViewInstanceState);
    outState.putBoolean(getContext().getString(R.string.navigation_running), navigationViewModel.isRunning());
    mapView.onSaveInstanceState(outState);
    saveNavigationMapInstanceState(outState);
  }

  /**
   * Used to restore the bottomsheet state and re-center
   * button visibility.  As well as the {@link MapView}
   * position prior to rotation.
   *
   * @param savedInstanceState to extract state variables
   */
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    String instanceKey = getContext().getString(R.string.navigation_view_instance_state);
    NavigationViewInstanceState navigationViewInstanceState = savedInstanceState.getParcelable(instanceKey);
    recenterBtn.setVisibility(navigationViewInstanceState.getRecenterButtonVisibility());
    wayNameView.setVisibility(navigationViewInstanceState.isWayNameVisible() ? VISIBLE : INVISIBLE);
    wayNameView.updateWayNameText(navigationViewInstanceState.getWayNameText());
    resetBottomSheetState(navigationViewInstanceState.getBottomSheetBehaviorState());
    updateInstructionListState(navigationViewInstanceState.isInstructionViewVisible());
    updateInstructionMutedState(navigationViewInstanceState.isMuted());
    mapInstanceState = savedInstanceState.getParcelable(MAP_INSTANCE_STATE_KEY);
  }

  /**
   * Called to ensure the {@link MapView} is destroyed
   * properly.
   * <p>
   * In an {@link Activity} this should be in {@link Activity#onDestroy()}.
   * <p>
   * In a {@link Fragment}, this should
   * be in {@link Fragment#onDestroyView()}.
   */
  @UiThread
  public void onDestroy() {
    shutdown();
    lifecycleRegistry.markState(Lifecycle.State.DESTROYED);
  }

  public void onStart() {
    mapView.onStart();
    if (navigationMap != null) {
      navigationMap.onStart();
    }
    lifecycleRegistry.markState(Lifecycle.State.STARTED);
  }

  public void onResume() {
    mapView.onResume();
    lifecycleRegistry.markState(Lifecycle.State.RESUMED);
  }

  public void onPause() {
    mapView.onPause();
  }

  public void onStop() {
    mapView.onStop();
    if (navigationMap != null) {
      navigationMap.onStop();
    }
  }

  @NonNull
  @Override
  public Lifecycle getLifecycle() {
    return lifecycleRegistry;
  }

  /**
   * Fired after the map is ready, this is our cue to finish
   * setting up the rest of the plugins / location engine.
   * <p>
   * Also, we check for launch data (coordinates or route).
   *
   * @param mapLibreMap used for route, camera, and location UI
   * @since 0.6.0
   */
  @Override
  public void onMapReady(final MapLibreMap mapLibreMap) {

    String style = null;
    if (isDarkMode(getContext())) {
      if (darkStyleUrl != null) {
        style = darkStyleUrl;
      } else {
        style = ThemeSwitcher.retrieveMapStyle(getContext());
      }
    } else {
      if (lightStyleUrl != null) {
        style = lightStyleUrl;
      } else {
        style = ThemeSwitcher.retrieveMapStyle(getContext());
      }
    }

    mapLibreMap.setStyle(style, new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        initializeNavigationMap(mapView, mapLibreMap);
        initializeWayNameListener();
        onNavigationReadyCallback.onNavigationReady(navigationViewModel.isRunning());
        isMapInitialized = true;
      }
    });
  }

  @Override
  public void setSummaryBehaviorState(int state) {
    summaryBehavior.setState(state);
  }

  @Override
  public void setSummaryBehaviorHideable(boolean isHideable) {
    summaryBehavior.setHideable(isHideable);
  }

  @Override
  public boolean isSummaryBottomSheetHidden() {
    return summaryBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN;
  }

  @Override
  public void resetCameraPosition() {
    if (navigationMap != null) {
      navigationMap.resetPadding();
      navigationMap.resetCameraPositionWith(NavigationCamera.NAVIGATION_TRACKING_MODE_GPS);
    }
  }

  @Override
  public void showRecenterBtn() {
    recenterBtn.show();
  }

  @Override
  public void hideRecenterBtn() {
    recenterBtn.hide();
  }

  @Override
  public boolean isRecenterButtonVisible() {
    return recenterBtn.getVisibility() == View.VISIBLE;
  }

  @Override
  public void drawRoute(DirectionsRoute directionsRoute) {
    if (navigationMap != null) {
      navigationMap.drawRoute(directionsRoute);
    }
  }

  @Override
  public void addMarker(Point position) {
    if (navigationMap != null) {
      navigationMap.addDestinationMarker(position);
    }
  }

  /**
   * Provides the current visibility of the way name view.
   *
   * @return true if visible, false if not visible
   */
  public boolean isWayNameVisible() {
    return wayNameView.getVisibility() == VISIBLE;
  }

  /**
   * Updates the text of the way name view below the
   * navigation icon.
   * <p>
   * If you'd like to use this method without being overridden by the default way names
   * values we provide, please disabled auto-query with
   * {@link NavigationMapLibreMap#updateWaynameQueryMap(boolean)}.
   *
   * @param wayName to update the view
   */
  @Override
  public void updateWayNameView(@NonNull String wayName) {
    wayNameView.updateWayNameText(wayName);
  }

  /**
   * Updates the visibility of the way name view that is show below
   * the navigation icon.
   * <p>
   * If you'd like to use this method without being overridden by the default visibility values
   * values we provide, please disabled auto-query with
   * {@link NavigationMapLibreMap#updateWaynameQueryMap(boolean)}.
   *
   * @param isVisible true to show, false to hide
   */
  @Override
  public void updateWayNameVisibility(boolean isVisible) {
    if (TextUtils.isEmpty(wayNameView.retrieveWayNameText())) {
      isVisible = false;
    }
    wayNameView.updateVisibility(isVisible);
    if (navigationMap != null) {
      navigationMap.updateWaynameQueryMap(isVisible);
    }
  }

  /**
   * Used when starting this {@link android.app.Activity}
   * for the first time.
   * <p>
   * Zooms to the beginning of the {@link DirectionsRoute}.
   *
   * @param directionsRoute where camera should move to
   */
  @Override
  public void startCamera(DirectionsRoute directionsRoute) {
    if (navigationMap != null) {
      navigationMap.startCamera(directionsRoute);
    }
  }

  /**
   * Used after configuration changes to resume the camera
   * to the last location update from the Navigation SDK.
   *
   * @param location where the camera should move to
   */
  @Override
  public void resumeCamera(Location location) {
    if (navigationMap != null) {
      navigationMap.resumeCamera(location);
    }
  }

  @Override
  public void updateNavigationMap(Location location) {
    if (navigationMap != null) {
      navigationMap.updateLocation(location);
    }
  }

  @Override
  public void updateCameraRouteOverview() {
    if (navigationMap != null) {
      int[] padding = buildRouteOverviewPadding(getContext());
      navigationMap.showRouteOverview(padding);
    }
  }

  /**
   * Should be called when this view is completely initialized.
   *
   * @param options with containing route / coordinate data
   */
  public void startNavigation(NavigationViewOptions options) {
    initializeNavigation(options);
  }

  /**
   * Call this when the navigation session needs to end navigation without finishing the whole view
   *
   * @since 0.16.0
   */
  public void stopNavigation() {
    navigationPresenter.onNavigationStopped();
    navigationViewModel.stopNavigation();
  }

  /**
   * Should be called after {@link NavigationView#onCreate(Bundle)}.
   * <p>
   * This method adds the {@link OnNavigationReadyCallback},
   * which will fire the ready events for this view.
   *
   * @param onNavigationReadyCallback to be set to this view
   */
  public void initialize(OnNavigationReadyCallback onNavigationReadyCallback) {
    this.onNavigationReadyCallback = onNavigationReadyCallback;
    if (!isMapInitialized) {
      mapView.getMapAsync(this);
    } else {
      onNavigationReadyCallback.onNavigationReady(navigationViewModel.isRunning());
    }
  }

  /**
   * Should be called after {@link NavigationView#onCreate(Bundle)}.
   * <p>
   * This method adds the {@link OnNavigationReadyCallback},
   * which will fire the ready events for this view.
   * <p>
   * This method also accepts a {@link CameraPosition} that will be set as soon as the map is
   * ready.  Note, this position is ignored during rotation in favor of the last known map position.
   *
   * @param onNavigationReadyCallback to be set to this view
   * @param initialMapCameraPosition  to be shown once the map is ready
   */
  public void initialize(OnNavigationReadyCallback onNavigationReadyCallback,
                         @NonNull CameraPosition initialMapCameraPosition) {
    this.onNavigationReadyCallback = onNavigationReadyCallback;
    this.initialMapCameraPosition = initialMapCameraPosition;
    if (!isMapInitialized) {
      mapView.getMapAsync(this);
    } else {
      onNavigationReadyCallback.onNavigationReady(navigationViewModel.isRunning());
    }
  }

  /**
   * Gives the ability to manipulate the map directly for anything that might not currently be
   * supported. This returns null until the view is initialized.
   * <p>
   * The {@link NavigationMapLibreMap} gives direct access to the map UI (location marker, route, etc.).
   *
   * @return navigation MapLibre map object, or null if view has not been initialized
   */
  @Nullable
  public NavigationMapLibreMap retrieveNavigationmapLibreMap() {
    return navigationMap;
  }

  /**
   * Returns the instance of {@link MapLibreNavigation} powering the {@link NavigationView}
   * once navigation has started.  Will return null if navigation has not been started with
   * {@link NavigationView#startNavigation(NavigationViewOptions)}.
   *
   * @return MapLibre navigation, or null if navigation has not started
   */
  @Nullable
  public MapLibreNavigation retrieveMapLibreNavigation() {
    return navigationViewModel.retrieveNavigation();
  }

  /**
   * Returns the sound button used for muting instructions
   *
   * @return sound button
   */
  public NavigationButton retrieveSoundButton() {
    return instructionView.retrieveSoundButton();
  }


  /**
   * Returns the re-center button for recentering on current location
   *
   * @return recenter button
   */
  public NavigationButton retrieveRecenterButton() {
    return recenterBtn;
  }

  /**
   * Returns the {@link NavigationAlertView} that is shown during off-route events with
   * "Report a Problem" text.
   *
   * @return alert view that is used in the instruction view
   */
  public NavigationAlertView retrieveAlertView() {
    return instructionView.retrieveAlertView();
  }

  private void initializeView() {
    inflate(getContext(), R.layout.navigation_view_layout, this);
    bind();
    initializeNavigationViewModel();
    initializeNavigationEventDispatcher();
    initializeNavigationPresenter();
    initializeInstructionListListener();
    initializeSummaryBottomSheet();
  }

  private void bind() {
    mapView = findViewById(R.id.navigationMapView);
    instructionView = findViewById(R.id.instructionView);
    ViewCompat.setElevation(instructionView, 10);
    summaryBottomSheet = findViewById(R.id.summaryBottomSheet);
    cancelBtn = findViewById(R.id.cancelBtn);
    recenterBtn = findViewById(R.id.recenterBtn);
    wayNameView = findViewById(R.id.wayNameView);
    routeOverviewBtn = findViewById(R.id.routeOverviewBtn);
  }

  private void initializeNavigationViewModel() {
    try {
      navigationViewModel = new ViewModelProvider((FragmentActivity) getContext()).get(NavigationViewModel.class);
    } catch (ClassCastException exception) {
      throw new ClassCastException("Please ensure that the provided Context is a valid FragmentActivity");
    }
  }

  private void initializeSummaryBottomSheet() {
    summaryBehavior = BottomSheetBehavior.from(summaryBottomSheet);
    summaryBehavior.setHideable(false);
    summaryBehavior.setBottomSheetCallback(new SummaryBottomSheetCallback(navigationPresenter,
      navigationViewEventDispatcher));
  }

  private void initializeNavigationEventDispatcher() {
    navigationViewEventDispatcher = new NavigationViewEventDispatcher();
    navigationViewModel.initializeEventDispatcher(navigationViewEventDispatcher);
  }

  private void initializeInstructionListListener() {
    instructionView.setInstructionListListener(new NavigationInstructionListListener(navigationPresenter,
      navigationViewEventDispatcher));
  }

  private void initializeNavigationMap(MapView mapView, MapLibreMap map) {
    if (initialMapCameraPosition != null) {
      map.setCameraPosition(initialMapCameraPosition);
    }
    navigationMap = new NavigationMapLibreMap(mapView, map);
    navigationMap.updateLocationLayerRenderMode(RenderMode.GPS);
    if (mapInstanceState != null) {
      navigationMap.restoreFrom(mapInstanceState);
      return;
    }
  }

  private void initializeWayNameListener() {
    NavigationViewWayNameListener wayNameListener = new NavigationViewWayNameListener(navigationPresenter);
    navigationMap.addOnWayNameChangedListener(wayNameListener);
  }

  private void saveNavigationMapInstanceState(Bundle outState) {
    if (navigationMap != null) {
      navigationMap.saveStateWith(MAP_INSTANCE_STATE_KEY, outState);
    }
  }

  private void resetBottomSheetState(int bottomSheetState) {
    if (bottomSheetState > INVALID_STATE) {
      boolean isShowing = bottomSheetState == BottomSheetBehavior.STATE_EXPANDED;
      summaryBehavior.setHideable(!isShowing);
      summaryBehavior.setState(bottomSheetState);
    }
  }

  private void updateInstructionListState(boolean visible) {
    if (visible) {
      instructionView.showInstructionList();
    } else {
      instructionView.hideInstructionList();
    }
  }

  private void updateInstructionMutedState(boolean isMuted) {
    if (isMuted) {
      ((SoundButton) instructionView.retrieveSoundButton()).soundFabOff();
    }
  }

  private int[] buildRouteOverviewPadding(Context context) {
    Resources resources = context.getResources();
    int leftRightPadding = (int) resources.getDimension(R.dimen.route_overview_left_right_padding);
    int paddingBuffer = (int) resources.getDimension(R.dimen.route_overview_buffer_padding);
    int instructionHeight = (int) (resources.getDimension(R.dimen.instruction_layout_height) + paddingBuffer);
    int summaryHeight = (int) resources.getDimension(R.dimen.summary_bottomsheet_height);
    return new int[] {leftRightPadding, instructionHeight, leftRightPadding, summaryHeight};
  }

  private boolean isChangingConfigurations() {
    try {
      return ((FragmentActivity) getContext()).isChangingConfigurations();
    } catch (ClassCastException exception) {
      throw new ClassCastException("Please ensure that the provided Context is a valid FragmentActivity");
    }
  }

  private void initializeNavigationPresenter() {
    navigationPresenter = new NavigationPresenter(this);
  }

  private void updatePresenterState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      String navigationRunningKey = getContext().getString(R.string.navigation_running);
      boolean resumeState = savedInstanceState.getBoolean(navigationRunningKey);
      navigationPresenter.updateResumeState(resumeState);
    }
  }

  private void initializeNavigation(NavigationViewOptions options) {
    lightStyleUrl = options.lightStyleUrl();
    darkStyleUrl = options.darkStyleUrl();
    establish(options);
    navigationViewModel.initialize(options);
    initializeNavigationListeners(options, navigationViewModel);
    setupNavigationmapLibreMap(options);

    if (!isSubscribed) {
      initializeClickListeners();
      initializeOnCameraTrackingChangedListener();
      subscribeViewModels();
    }
  }

  private void initializeClickListeners() {
    cancelBtn.setOnClickListener(new CancelBtnClickListener(navigationViewEventDispatcher));
    recenterBtn.addOnClickListener(new RecenterBtnClickListener(navigationPresenter));
    routeOverviewBtn.setOnClickListener(new RouteOverviewBtnClickListener(navigationPresenter));
  }

  private void initializeOnCameraTrackingChangedListener() {
    onTrackingChangedListener = new NavigationOnCameraTrackingChangedListener(navigationPresenter, summaryBehavior);
    navigationMap.addOnCameraTrackingChangedListener(onTrackingChangedListener);
  }

  private void establish(NavigationViewOptions options) {
    LocaleUtils localeUtils = new LocaleUtils();
    establishDistanceFormatter(localeUtils, options);
    establishTimeFormat(options);
  }

  private void establishDistanceFormatter(LocaleUtils localeUtils, NavigationViewOptions options) {
    UnitType unitType = establishUnitType(localeUtils, options);
    String language = establishLanguage(localeUtils, options);
    MapLibreNavigationOptions.RoundingIncrement roundingIncrement = establishRoundingIncrement(options);
    DistanceFormatter distanceFormatter = new DistanceFormatter(getContext(), language, unitType, roundingIncrement);

    instructionView.setDistanceFormatter(distanceFormatter);
    summaryBottomSheet.setDistanceFormatter(distanceFormatter);
  }

  private MapLibreNavigationOptions.RoundingIncrement establishRoundingIncrement(NavigationViewOptions navigationViewOptions) {
    MapLibreNavigationOptions mapLibreNavigationOptions = navigationViewOptions.navigationOptions();
    return mapLibreNavigationOptions.getRoundingIncrement();
  }

  private String establishLanguage(LocaleUtils localeUtils, NavigationViewOptions options) {
    return localeUtils.getNonEmptyLanguage(getContext(), options.directionsRoute().getVoiceLanguage());
  }

  private UnitType establishUnitType(LocaleUtils localeUtils, NavigationViewOptions options) {
    RouteOptions routeOptions = options.directionsRoute().getRouteOptions();
    UnitType voiceUnits = routeOptions == null ? null : routeOptions.getVoiceUnits();
    return localeUtils.retrieveNonNullUnitType(getContext(), voiceUnits);
  }

  private void establishTimeFormat(NavigationViewOptions options) {
    summaryBottomSheet.setTimeFormat(options.navigationOptions().getTimeFormatType());
  }

  private void initializeNavigationListeners(NavigationViewOptions options, NavigationViewModel navigationViewModel) {
    navigationMap.addProgressChangeListener(navigationViewModel.retrieveNavigation());
    navigationViewEventDispatcher.initializeListeners(options, navigationViewModel);
  }

  private void setupNavigationmapLibreMap(NavigationViewOptions options) {
    navigationMap.updateWaynameQueryMap(options.waynameChipEnabled());
  }

  /**
   * Subscribes the {@link InstructionView} and {@link SummaryBottomSheet} to the {@link NavigationViewModel}.
   * <p>
   * Then, creates an instance of {@link NavigationViewSubscriber}, which takes a presenter.
   * <p>
   * The subscriber then subscribes to the view models, setting up the appropriate presenter / listener
   * method calls based on the {@link androidx.lifecycle.LiveData} updates.
   */
  private void subscribeViewModels() {
    instructionView.subscribe(this, navigationViewModel);
    summaryBottomSheet.subscribe(this, navigationViewModel);

    new NavigationViewSubscriber(this, navigationViewModel, navigationPresenter).subscribe();
    isSubscribed = true;
  }

  @UiThread
  private void shutdown() {
    if (navigationMap != null) {
      navigationMap.removeOnCameraTrackingChangedListener(onTrackingChangedListener);
      navigationMap.onDestroy();
    }
    navigationViewEventDispatcher.onDestroy(navigationViewModel.retrieveNavigation());
    mapView.onDestroy();
    navigationViewModel.onDestroy(isChangingConfigurations());
    ImageCreator.getInstance().shutdown();
    navigationMap = null;
  }

  public boolean isDarkMode(Context context) {
    int darkModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return darkModeFlags == Configuration.UI_MODE_NIGHT_YES;
  }
}
