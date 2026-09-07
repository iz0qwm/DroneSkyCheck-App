package it.droneskycheck.app.map

import it.droneskycheck.app.data.airawareness.PublishedDoa
import it.droneskycheck.app.ui.map.MapPoint
import org.maplibre.geojson.FeatureCollection

fun airAwarenessDoaFeatureCollection(doa: PublishedDoa?): FeatureCollection {
    if (
        doa == null ||
        !doa.lat.isFinite() ||
        !doa.lon.isFinite() ||
        !doa.radiusMeters.isFinite() ||
        doa.lat !in -90.0..90.0 ||
        doa.lon !in -180.0..180.0 ||
        doa.radiusMeters <= 0.0
    ) {
        return emptyTrafficFeatureCollection()
    }

    return trafficRadiusFeatureCollection(
        center = MapPoint(lat = doa.lat, lon = doa.lon),
        radiusKm = doa.radiusMeters / METERS_PER_KILOMETER
    )
}

private const val METERS_PER_KILOMETER = 1_000.0
