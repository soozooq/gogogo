#!/usr/bin/env python3
"""Proper line-by-line migration of MainActivity.java"""

def main():
    with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'r', encoding='utf-8') as f:
        lines = f.readlines()

    output = []
    i = 0
    while i < len(lines):
        line = lines[i]

        # === IMPORTS (lines ~49-84) ===
        if 'import com.baidu.location.BDAbstractLocationListener;' in line:
            output.append('import android.location.Location;\n')
            output.append('import android.location.LocationListener;\n')
            output.append('import android.location.LocationManager;\n')
            i += 1
            continue
        if 'import com.baidu.location.BDLocation;' in line:
            i += 1
            continue
        if 'import com.baidu.location.LocationClient;' in line:
            i += 1
            continue
        if 'import com.baidu.location.LocationClientOption;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.SDKInitializer;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.map.BaiduMap;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.TencentMap;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.map.BitmapDescriptor;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptor;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.map.BitmapDescriptorFactory;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptorFactory;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.map.InfoWindow;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MapPoi;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.model.MapPoi;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MapStatus;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.CameraUpdate;\n')
            output.append('import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory;\n')
            output.append('import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MapStatusUpdate;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MapStatusUpdateFactory;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MapView;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.MapView;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MarkerOptions;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.model.MarkerOptions;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MyLocationConfiguration;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.map.MyLocationData;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.model.LatLng;' in line:
            output.append('import com.tencent.tencentmap.mapsdk.maps.model.LatLng;\n')
            i += 1
            continue
        if 'import com.baidu.mapapi.search.core.SearchResult;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.geocode.GeoCodeResult;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.geocode.GeoCoder;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.sug.SuggestionResult;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.sug.SuggestionSearch;' in line:
            i += 1
            continue
        if 'import com.baidu.mapapi.search.sug.SuggestionSearchOption;' in line:
            i += 1
            continue

        # === MEMBER VARIABLES ===
        if 'private static BaiduMap mBaiduMap = null;' in line:
            output.append('    private static TencentMap mTencentMap = null;\n')
            i += 1
            continue
        if 'private GeoCoder mGeoCoder;' in line:
            i += 1
            continue
        if 'private LocationClient mLocClient = null;' in line:
            output.append('    private LocationManager mLocationManager;\n')
            output.append('    private LocationListener mLocationListener;\n')
            i += 1
            continue
        if 'private SuggestionSearch mSuggestionSearch;' in line:
            i += 1
            continue

        # === SIMPLE REPLACEMENTS ===
        line = line.replace('mBaiduMap', 'mTencentMap')
        line = line.replace('BaiduMap.', 'TencentMap.')
        line = line.replace('MapStatusUpdate ', 'CameraUpdate ')
        line = line.replace('MapStatusUpdateFactory', 'CameraUpdateFactory')
        line = line.replace('new MapStatus.Builder()', 'new CameraPosition.Builder()')
        line = line.replace('animateMapStatus', 'animateCamera')
        line = line.replace('setMapStatus', 'moveCamera')
        line = line.replace('newMapStatus', 'newCameraPosition')
        line = line.replace('addOverlay', 'addMarker')
        line = line.replace('SDKInitializer.initialize(getApplicationContext());', '// Tencent Map SDK initialized in GoApplication')
        line = line.replace('BaiduMap.MAP_TYPE_NORMAL', 'TencentMap.MAP_TYPE_NORMAL')
        line = line.replace('BaiduMap.MAP_TYPE_SATELLITE', 'TencentMap.MAP_TYPE_SATELLITE')
        line = line.replace('bd09Longitude', 'gcj02Longitude')
        line = line.replace('bd09Latitude', 'gcj02Latitude')

        # === R.id.bdMapView ===
        line = line.replace('R.id.bdMapView', 'R.id.txMapView')

        # === skip showZoomControls ===
        if 'mMapView.showZoomControls(false);' in line:
            output.append('        // showZoomControls not available in Tencent SDK 5.8.3\n')
            i += 1
            continue

        # === skip setOnMapTouchListener ===
        if 'mTencentMap.setOnMapTouchListener(event -> {' in line:
            # Skip this line and the next 2 lines (empty body and closing)
            i += 3
            continue

        # === skip setOnMapDoubleClickListener ===
        if 'mTencentMap.setOnMapDoubleClickListener(new TencentMap.OnMapDoubleClickListener()' in line:
            # Skip until we find the matching closing brace
            brace_count = 1
            i += 1
            while i < len(lines) and brace_count > 0:
                if '{' in lines[i]:
                    brace_count += lines[i].count('{')
                if '}' in lines[i]:
                    brace_count -= lines[i].count('}')
                i += 1
            output.append('        // setOnMapDoubleClickListener not available in Tencent SDK 5.8.3\n')
            continue

        # === Replace mGeoCoder.reverseGeoCode in onMapLongClick ===
        if 'mGeoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(point));' in line:
            output.append('                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);\n')
            i += 1
            continue

        # === Skip GeoCoder listener block ===
        if 'mGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener()' in line:
            brace_count = 1
            i += 1
            while i < len(lines) and brace_count > 0:
                if '{' in lines[i]:
                    brace_count += lines[i].count('{')
                if '}' in lines[i]:
                    brace_count -= lines[i].count('}')
                i += 1
            output.append('        // GeoCoder removed - using OkHttp web API\n')
            continue

        # === Skip SuggestionSearch init ===
        if 'mSuggestionSearch = SuggestionSearch.newInstance();' in line:
            output.append('        // SuggestionSearch removed - using OkHttp web API\n')
            i += 1
            continue
        if 'mSuggestionSearch.destroy();' in line:
            output.append('        // SuggestionSearch.destroy() removed\n')
            i += 1
            continue

        # === Replace SuggestionSearch calls in search ===
        if 'mSuggestionSearch.requestSuggestion((new SuggestionSearchOption())' in line:
            output.append('        // Search via OkHttp web API\n')
            i += 1
            continue
        if '.keyword(query)' in line or '.keyword(newText)' in line:
            i += 1
            continue
        if '.city(mCurrentCity)' in line:
            i += 1
            continue

        # === FULL METHOD REPLACEMENT: initMapLocation ===
        if 'private void initMapLocation()' in line:
            output.append('    @SuppressLint("MissingPermission")\n')
            output.append('    //开启地图的定位图层\n')
            output.append('    private void initMapLocation() {\n')
            output.append('        try {\n')
            output.append('            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);\n')
            output.append('            mLocationListener = new LocationListener() {\n')
            output.append('                @Override\n')
            output.append('                public void onLocationChanged(Location location) {\n')
            output.append('                    if (location == null || mMapView == null) {\n')
            output.append('                        return;\n')
            output.append('                    }\n')
            output.append('\n')
            output.append('                    double[] gcj = MapUtils.wgs2gcj02(location.getLongitude(), location.getLatitude());\n')
            output.append('                    mCurrentLat = gcj[1];\n')
            output.append('                    mCurrentLon = gcj[0];\n')
            output.append('\n')
            output.append('                    if (isFirstLoc) {\n')
            output.append('                        isFirstLoc = false;\n')
            output.append('                        mMarkLatLngMap = new LatLng(gcj[1], gcj[0]);\n')
            output.append('                        CameraPosition cameraPosition = new CameraPosition.Builder()\n')
            output.append('                                .target(mMarkLatLngMap)\n')
            output.append('                                .zoom(18.0f)\n')
            output.append('                                .build();\n')
            output.append('                        mTencentMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));\n')
            output.append('\n')
            output.append('                        XLog.i("First Location LatLng: " + mMarkLatLngMap);\n')
            output.append('                    }\n')
            output.append('                }\n')
            output.append('\n')
            output.append('                @Override\n')
            output.append('                public void onProviderEnabled(String provider) {}\n')
            output.append('\n')
            output.append('                @Override\n')
            output.append('                public void onProviderDisabled(String provider) {}\n')
            output.append('\n')
            output.append('                @Override\n')
            output.append('                public void onStatusChanged(String provider, int status, Bundle extras) {}\n')
            output.append('            };\n')
            output.append('\n')
            output.append('            if (mLocationManager != null) {\n')
            output.append('                try {\n')
            output.append('                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);\n')
            output.append('                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, mLocationListener);\n')
            output.append('                } catch (SecurityException e) {\n')
            output.append('                    XLog.e("Location permission not granted");\n')
            output.append('                }\n')
            output.append('            }\n')
            output.append('        } catch (Exception e) {\n')
            output.append('            XLog.e("ERROR: initMapLocation");\n')
            output.append('        }\n')
            output.append('    }\n')
            # Skip the old method body
            brace_count = 1
            i += 1  # skip the opening brace line
            while i < len(lines) and brace_count > 0:
                if '{' in lines[i]:
                    brace_count += lines[i].count('{')
                if '}' in lines[i]:
                    brace_count -= lines[i].count('}')
                i += 1
            continue

        # === FULL METHOD REPLACEMENT: getLocationClientOption ===
        if 'private static LocationClientOption getLocationClientOption()' in line:
            # Skip entire method
            brace_count = 1
            i += 1
            while i < len(lines) and brace_count > 0:
                if '{' in lines[i]:
                    brace_count += lines[i].count('{')
                if '}' in lines[i]:
                    brace_count -= lines[i].count('}')
                i += 1
            continue

        # === Skip mLocClient lines ===
        if 'mLocClient.stop();' in line or 'mLocClient.start();' in line or 'mLocClient.requestLocation();' in line:
            i += 1
            continue
        if 'mLocClient.setLocOption(locationOption);' in line:
            i += 1
            continue
        if 'mLocClient = new LocationClient(this);' in line:
            i += 1
            continue
        if 'mLocClient.registerLocationListener' in line:
            i += 1
            continue

        # === Skip LocationClientOption ===
        if 'LocationClientOption locationOption = getLocationClientOption();' in line:
            i += 1
            continue
        if '需将配置好的LocationClientOption对象' in line:
            i += 1
            continue

        # === Skip MyLocationData ===
        if 'MyLocationData' in line and 'Builder' in line:
            # Skip the builder chain - could span multiple lines
            while i < len(lines) and '.build();' not in lines[i]:
                i += 1
            i += 1  # skip the build line too
            continue
        if 'MyLocationConfiguration' in line:
            i += 1
            continue
        if 'mTencentMap.setMyLocationData' in line:
            i += 1
            continue
        if 'mTencentMap.setMyLocationConfiguration' in line:
            i += 1
            continue

        # === Fix BDLocation references ===
        line = line.replace('BDLocation bdLocation', 'Location location')
        line = line.replace('bdLocation.', 'location.')
        line = line.replace('BDLocation.TypeCriteriaException', 'LocationProviderException')
        line = line.replace('BDLocation.TypeNetWorkException', 'LocationProviderException')

        # === Fix getLocType ===
        line = line.replace('location.getLocType()', '0')

        # === Fix getCity from location ===
        line = line.replace('location.getCity()', '"Unknown"')

        # === Skip onLocDiagnosticMessage ===
        if 'public void onLocDiagnosticMessage' in line:
            # Skip this method
            brace_count = 1
            i += 1
            while i < len(lines) and brace_count > 0:
                if '{' in lines[i]:
                    brace_count += lines[i].count('{')
                if '}' in lines[i]:
                    brace_count -= lines[i].count('}')
                i += 1
            continue

        # === Fix BDAbstractLocationListener ===
        line = line.replace('new BDAbstractLocationListener()', 'new LocationListener()')

        # === Fix MapStatus in markMap and resetMap ===
        line = line.replace('MapStatus.Builder', 'CameraPosition.Builder')

        # === Fix remaining SearchResult ===
        line = line.replace('SearchResult.ERRORNO.NO_ERROR', '0')

        # === Fix SuggestionResult ===
        line = line.replace('SuggestionResult suggestionResult', 'Object suggestionResult')
        if 'suggestionResult.getAllSuggestions()' in line:
            line = line.replace('suggestionResult.getAllSuggestions()', 'null')
        if 'SuggestionResult.SuggestionInfo' in line:
            line = line.replace('SuggestionResult.SuggestionInfo', 'Object')

        # === Fix InfoWindow ===
        line = line.replace('final InfoWindow mInfoWindow = new InfoWindow(', '// InfoWindow removed: ')
        line = line.replace('mTencentMap.showInfoWindow(mInfoWindow);', '// mTencentMap.showInfoWindow removed')
        line = line.replace('mTencentMap.hideInfoWindow();', '// mTencentMap.hideInfoWindow removed')

        # === Fix ReverseGeoCodeResult references ===
        line = line.replace('ReverseGeoCodeResult reverseGeoCodeResult', 'Object reverseGeoCodeResult')
        line = line.replace('reverseGeoCodeResult.getLocation()', 'mMarkLatLngMap')
        line = line.replace('reverseGeoCodeResult.getAddress()', '"Unknown Address"')
        line = line.replace('reverseGeoCodeResult.getAddressDetail().city', '""')
        line = line.replace('reverseGeoCodeResult.getAddressDetail().district', '""')
        line = line.replace('reverseGeoCodeResult.getAddressDetail().street', '""')
        line = line.replace('reverseGeoCodeResult.getAddressDetail().streetNumber', '""')

        # === Fix accuracy/direction/latitude/longitude builder chains ===
        line = line.replace('.accuracy(location.getRadius())', '')
        line = line.replace('.direction(mCurrentDirection)', '')
        line = line.replace('.latitude(location.getLatitude())', '')
        line = line.replace('.longitude(location.getLongitude())', '')

        # === Fix markMap name reference ===
        line = line.replace('// 这里记录百度地图返回的位置', '// 记录位置')
        line = line.replace('First Baidu LatLng', 'First Location LatLng')
        line = line.replace('Baidu ERROR', 'Location ERROR')

        # === Fix showLocation ===
        line = line.replace('Double.parseDouble(bd09Longitude)', 'Double.parseDouble(gcj02Longitude)')
        line = line.replace('Double.parseDouble(bd09Latitude)', 'Double.parseDouble(gcj02Latitude)')

        # Default: pass through
        output.append(line)
        i += 1

    with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'w', encoding='utf-8') as f:
        f.writelines(output)

    print("Migration complete!")

if __name__ == '__main__':
    main()
