package org.maplibre.navigation.android.navigation.ui.v5;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback;
import com.google.auto.value.AutoValue;

import org.maplibre.navigation.android.navigation.ui.v5.listeners.BannerInstructionsListener;
import org.maplibre.navigation.android.navigation.ui.v5.listeners.InstructionListListener;
import org.maplibre.navigation.android.navigation.ui.v5.listeners.NavigationListener;
import org.maplibre.navigation.android.navigation.ui.v5.listeners.RouteListener;
import org.maplibre.navigation.android.navigation.ui.v5.listeners.SpeechAnnouncementListener;
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechPlayer;
import org.maplibre.navigation.core.location.engine.LocationEngine;
import org.maplibre.navigation.core.models.DirectionsRoute;
import org.maplibre.navigation.core.milestone.Milestone;
import org.maplibre.navigation.core.milestone.MilestoneEventListener;
import org.maplibre.navigation.core.navigation.MapLibreNavigationOptions;
import org.maplibre.navigation.core.routeprogress.ProgressChangeListener;

import java.util.List;

@AutoValue
public abstract class NavigationViewOptions extends NavigationUiOptions {

  public abstract MapLibreNavigationOptions navigationOptions();

  @Nullable
  public abstract RouteListener routeListener();

  @Nullable
  public abstract NavigationListener navigationListener();

  @Nullable
  public abstract ProgressChangeListener progressChangeListener();

  @Nullable
  public abstract MilestoneEventListener milestoneEventListener();

  @Nullable
  public abstract List<Milestone> milestones();

  @Nullable
  public abstract BottomSheetCallback bottomSheetCallback();

  @Nullable
  public abstract InstructionListListener instructionListListener();

  @Nullable
  public abstract SpeechAnnouncementListener speechAnnouncementListener();

  @Nullable
  public abstract BannerInstructionsListener bannerInstructionsListener();

  @Nullable
  public abstract SpeechPlayer speechPlayer();

  @Nullable
  public abstract LocationEngine locationEngine();

  @Nullable
  public abstract String lightStyleUrl();

  @Nullable
  public abstract String darkStyleUrl();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder directionsRoute(DirectionsRoute directionsRoute);

    public abstract Builder lightThemeResId(Integer lightThemeResId);

    public abstract Builder darkThemeResId(Integer darkThemeResId);

    public abstract Builder shouldSimulateRoute(boolean shouldSimulateRoute);

    public abstract Builder waynameChipEnabled(boolean waynameChipEnabled);

    public abstract Builder navigationOptions(MapLibreNavigationOptions navigationOptions);

    public abstract Builder routeListener(RouteListener routeListener);

    public abstract Builder navigationListener(NavigationListener navigationListener);

    public abstract Builder progressChangeListener(ProgressChangeListener progressChangeListener);

    public abstract Builder milestoneEventListener(MilestoneEventListener milestoneEventListener);

    public abstract Builder milestones(List<Milestone> milestones);

    public abstract Builder bottomSheetCallback(BottomSheetCallback bottomSheetCallback);

    public abstract Builder instructionListListener(InstructionListListener instructionListListener);

    public abstract Builder speechAnnouncementListener(SpeechAnnouncementListener speechAnnouncementListener);

    public abstract Builder bannerInstructionsListener(BannerInstructionsListener bannerInstructionsListener);

    public abstract Builder speechPlayer(SpeechPlayer speechPlayer);

    public abstract Builder locationEngine(LocationEngine locationEngine);

    public abstract Builder lightStyleUrl(String lightStyleUrl);

    public abstract Builder darkStyleUrl(String darkStyleUrl);

    public abstract NavigationViewOptions build();
  }

  public static Builder builder() {
    return new AutoValue_NavigationViewOptions.Builder()
      .navigationOptions(new MapLibreNavigationOptions())
      .shouldSimulateRoute(false)
      .waynameChipEnabled(true);
  }
}