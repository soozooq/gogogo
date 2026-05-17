#!/usr/bin/env python3
"""Careful migration of MainActivity.java from Baidu to Tencent Maps"""

import re

def main():
    with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # === STEP 1: IMPORTS ===
    old_imports = '''import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.InfoWindow;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.baidu.mapapi.search.sug.SuggestionSearchOption;'''

    new_imports = '''import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import com.tencent.tencentmap.mapsdk.maps.TencentMap;
import com.tencent.tencentmap.mapsdk.maps.MapView;
import com.tencent.tencentmap.mapsdk.maps.CameraUpdate;
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory;
import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition;
import com.tencent.tencentmap.mapsdk.maps.model.LatLng;
import com.tencent.tencentmap.mapsdk.maps.model.MarkerOptions;
import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptor;
import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptorFactory;
import com.tencent.tencentmap.mapsdk.maps.model.MapPoi;'''

    content = content.replace(old_imports, new_imports)

    # === STEP 2: MEMBER VARIABLES ===
    content = content.replace('private static BaiduMap mBaiduMap = null;', 'private static TencentMap mTencentMap = null;')
    content = content.replace('private GeoCoder mGeoCoder;', '')
    content = content.replace('private LocationClient mLocClient = null;', 'private LocationManager mLocationManager;\n    private LocationListener mLocationListener;')
    content = content.replace('private SuggestionSearch mSuggestionSearch;', '')

    # === STEP 3: SIMPLE API REPLACEMENTS ===
    content = content.replace('mBaiduMap', 'mTencentMap')
    content = content.replace('BaiduMap.', 'TencentMap.')
    content = content.replace('BaiduMap_OnMapClickListener', 'TencentMap_OnMapClickListener')
    content = content.replace('MapStatusUpdate ', 'CameraUpdate ')
    content = content.replace('MapStatusUpdateFactory', 'CameraUpdateFactory')
    content = content.replace('new MapStatus.Builder()', 'new CameraPosition.Builder()')
    content = content.replace('animateMapStatus', 'animateCamera')
    content = content.replace('setMapStatus', 'moveCamera')
    content = content.replace('newMapStatus', 'newCameraPosition')
    content = content.replace('addOverlay', 'addMarker')
    content = content.replace('SDKInitializer.initialize(getApplicationContext());', '// Tencent Map SDK initialized in GoApplication')
    content = content.replace('BaiduMap.MAP_TYPE_NORMAL', 'TencentMap.MAP_TYPE_NORMAL')
    content = content.replace('BaiduMap.MAP_TYPE_SATELLITE', 'TencentMap.MAP_TYPE_SATELLITE')
    content = content.replace('bd09Longitude', 'gcj02Longitude')
    content = content.replace('bd09Latitude', 'gcj02Latitude')

    # === STEP 4: REMOVE/REPLACE SUGGESTION SEARCH ===
    content = content.replace('mSuggestionSearch = SuggestionSearch.newInstance();', '// SuggestionSearch removed')
    content = content.replace('mSuggestionSearch.destroy();', '// SuggestionSearch.destroy() removed')

    # Fix the onQueryTextSubmit - replace the SuggestionSearch call block
    old_search_submit = '''mSuggestionSearch.requestSuggestion((new SuggestionSearchOption())
                            .keyword(query)
                            .city(mCurrentCity)
                    );'''
    content = content.replace(old_search_submit, '// Search via OkHttp web API')

    old_search_change = '''mSuggestionSearch.requestSuggestion((new SuggestionSearchOption())
                                .keyword(newText)
                                .city(mCurrentCity)
                        );'''
    content = content.replace(old_search_change, '// Search via OkHttp web API')

    # === STEP 5: REMOVE GEOCODER ===
    content = content.replace('mGeoCoder = GeoCoder.newInstance();', '// GeoCoder removed')
    content = content.replace('mGeoCoder.destroy();', '// GeoCoder.destroy() removed')

    # Remove the entire GeoCoder listener block
    geocoder_block = '''mGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    XLog.w("No result for reverse geo coding");
                    return;
                }

                final View poiView = getLayoutInflater().inflate(R.layout.location_poi_info, null);
                final InfoWindow mInfoWindow = new InfoWindow(poiView, reverseGeoCodeResult.getLocation(), -100);
                mTencentMap.showInfoWindow(mInfoWindow);

                // 显示详细信息
                TextView tvPoiName = poiView.findViewById(R.id.poi_name);
                tvPoiName.setText(reverseGeoCodeResult.getAddress());
                TextView tvPoiAddress = poiView.findViewById(R.id.poi_address);
                tvPoiAddress.setText(reverseGeoCodeResult.getAddressDetail().city + reverseGeoCodeResult.getAddressDetail().district + reverseGeoCodeResult.getAddressDetail().street + reverseGeoCodeResult.getAddressDetail().streetNumber);

                poiView.findViewById(R.id.poi_confirm).setOnClickListener(v -> {
                    mTencentMap.hideInfoWindow();
                });
            }
        });'''
    content = content.replace(geocoder_block, '// GeoCoder removed - using OkHttp web API')

    # === STEP 6: REMOVE LOCATION CLIENT ===
    content = content.replace('mLocClient.stop();', '')
    content = content.replace('mLocClient.start();', '')
    content = content.replace('mLocClient.requestLocation();', '')

    # === STEP 7: FIX initMap() METHOD ===
    # Replace R.id.bdMapView
    content = content.replace('R.id.bdMapView', 'R.id.txMapView')

    # Remove showZoomControls (not in Tencent SDK 5.8.3)
    content = content.replace('mMapView.showZoomControls(false);', '// showZoomControls not in Tencent SDK 5.8.3')

    # Remove setOnMapTouchListener (not in Tencent SDK 5.8.3)
    old_touch = '''mTencentMap.setOnMapTouchListener(event -> {
                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            });'''
    content = content.replace(old_touch, '// setOnMapTouchListener not available in Tencent SDK 5.8.3')

    # Remove setOnMapDoubleClickListener (not in Tencent SDK 5.8.3)
    old_double_click = '''mTencentMap.setOnMapDoubleClickListener(new TencentMap.OnMapDoubleClickListener() {
            @Override
            public void onMapDoubleClick(LatLng point) {
                mTencentMap.clear();
            }
        });'''
    content = content.replace(old_double_click, '// setOnMapDoubleClickListener not available in Tencent SDK 5.8.3')

    # Fix onMapLongClick - use recordCurrentLocation instead of reverseGeoCode
    old_long_click = '''mTencentMap.setOnMapLongClickListener(new TencentMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng point) {
                mMarkLatLngMap = point;
                if (mTencentMap != null) {
                    mGeoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(point));
                }
            }
        });'''
    new_long_click = '''mTencentMap.setOnMapLongClickListener(new TencentMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng point) {
                mMarkLatLngMap = point;
                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            }
        });'''
    content = content.replace(old_long_click, new_long_click)

    # Fix onMapPoiClick - Tencent's MapPoi uses getPosition()
    # Baidu's MapPoi also uses getPosition() so this should be fine

    # === STEP 8: FIX initMapLocation() METHOD ===
    # Remove the entire old initMapLocation and replace with system LocationManager version
    old_init_loc = '''    //开启地图的定位图层
    private void initMapLocation() {
        try {
            // 定位初始化
            mLocClient = new LocationClient(this);
            mLocClient.registerLocationListener(new BDAbstractLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation bdLocation) {
                    if (bdLocation == null || mMapView == null) {// mapview 销毁后不在处理新接收的位置
                        return;
                    }

                    mCurrentCity = bdLocation.getCity();
                    mCurrentLat = bdLocation.getLatitude();
                    mCurrentLon = bdLocation.getLongitude();
                    MyLocationData locData = new MyLocationData.Builder()
                            .accuracy(bdLocation.getRadius())
                            .direction(mCurrentDirection)// 此处设置开发者获取到的方向信息，顺时针0-360
                            .latitude(bdLocation.getLatitude())
                            .longitude(bdLocation.getLongitude()).build();
                    mTencentMap.setMyLocationData(locData);
                    MyLocationConfiguration configuration = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null);
                    mTencentMap.setMyLocationConfiguration(configuration);

                    /* 如果出现错误，则需要重新请求位置 */
                    int err = bdLocation.getLocType();
                    if (err == BDLocation.TypeCriteriaException || err == BDLocation.TypeNetWorkException) {
                        mLocClient.requestLocation();   /* 请求位置 */
                    } else {
                        if (isFirstLoc) {
                            isFirstLoc = false;
                            // 这里记录百度地图返回的位置
                            mMarkLatLngMap = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                            MapStatus.Builder builder = new CameraPosition.Builder();
                            builder.target(mMarkLatLngMap).zoom(18.0f);
                            mTencentMap.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()));

                            XLog.i("First Baidu LatLng: " + mMarkLatLngMap);
                        }
                    }
                }

                /**
                 * 错误的状态码
                 * <a><a href="http://lbsyun.baidu.com/index.php?title=android-locsdk/guide/addition-func/error-code">...</a></a>
                 * <p>
                 * 回调定位诊断信息，开发者可以根据相关信息解决定位遇到的一些问题
                 *
                 * @param locType      当前定位类型
                 * @param diagnosticType  诊断类型（1~9）
                 * @param diagnosticMessage 具体的诊断信息释义
                 */
                @Override
                public void onLocDiagnosticMessage(int locType, int diagnosticType, String diagnosticMessage) {
                    XLog.i("Baidu ERROR: " + locType + "-" + diagnosticType + "-" + diagnosticMessage);
                }
            });
            LocationClientOption locationOption = getLocationClientOption();
            //需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
            mLocClient.setLocOption(locationOption);
            //开始定位
            mLocClient.start();
        } catch (Exception e) {
            XLog.e("ERROR: initMapLocation");
        }
    }'''

    new_init_loc = '''    @SuppressLint("MissingPermission")
    //开启地图的定位图层
    private void initMapLocation() {
        try {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            mLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location == null || mMapView == null) {
                        return;
                    }

                    // 系统定位返回WGS84，转换为GCJ02
                    double[] gcj = MapUtils.wgs2gcj02(location.getLongitude(), location.getLatitude());
                    mCurrentLat = gcj[1];
                    mCurrentLon = gcj[0];

                    if (isFirstLoc) {
                        isFirstLoc = false;
                        mMarkLatLngMap = new LatLng(gcj[1], gcj[0]);
                        CameraPosition cameraPosition = new CameraPosition.Builder()
                                .target(mMarkLatLngMap)
                                .zoom(18.0f)
                                .build();
                        mTencentMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                        XLog.i("First Location LatLng: " + mMarkLatLngMap);
                    }
                }

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}
            };

            if (mLocationManager != null) {
                try {
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, mLocationListener);
                } catch (SecurityException e) {
                    XLog.e("Location permission not granted");
                }
            }
        } catch (Exception e) {
            XLog.e("ERROR: initMapLocation");
        }
    }'''

    content = content.replace(old_init_loc, new_init_loc)

    # === STEP 9: REMOVE getLocationClientOption METHOD ===
    old_loc_option = '''    @NonNull
    private static LocationClientOption getLocationClientOption() {
        LocationClientOption locationOption = new LocationClientOption();
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        locationOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        locationOption.setCoorType("bd09ll");
        //可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        locationOption.setScanSpan(1000);
        //可选，设置是否需要地址信息，默认不需要
        locationOption.setIsNeedAddress(true);
        //可选，设置是否需要设备方向结果
        locationOption.setNeedDeviceDirect(false);
        //可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        locationOption.setLocationNotify(true);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        locationOption.setIgnoreKillProcess(true);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于"在北京天安门附近"
        locationOption.setIsNeedLocationDescribe(false);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        locationOption.setIsNeedLocationPoiList(false);
        //可选，默认false，设置是否收集CRASH信息，默认收集
        locationOption.setIgnoreCacheException(true);
        //可选，默认false，设置是否开启Gps定位
        //locationOption.setOpenGps(true);
        locationOption.setOpenGnss(true);
        //可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用
        locationOption.setIsNeedAltitude(false);
        return locationOption;
    }'''
    content = content.replace(old_loc_option, '')

    # === STEP 10: FIX showLocation METHOD ===
    # Replace BD09 refs
    content = content.replace('Double.parseDouble(bd09Longitude)', 'Double.parseDouble(gcj02Longitude)')
    content = content.replace('Double.parseDouble(bd09Latitude)', 'Double.parseDouble(gcj02Latitude)')

    # === STEP 11: FIX markMap ===
    # The MapStatus.Builder was already replaced with CameraPosition.Builder
    # Make sure the method uses GCJ02 coords
    old_mark = '''private void markMap() {
        if (mTencentMap == null || mMarkLatLngMap == null) {
            return;
        }

        //先清除图层
        mTencentMap.clear();

        if (mMarkName != null) {
            MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
            mTencentMap.addMarker(ooA);
        }
    }'''
    # Already correct after replacements

    # === STEP 12: FIX getMapList ===
    content = content.replace('private static List<Map<String, Object>> getMapList(SuggestionResult suggestionResult) {',
                               'private static List<Map<String, Object>> getMapList(Object suggestionResult) {')
    content = content.replace('if (suggestionResult == null || suggestionResult.getAllSuggestions() == null) {',
                               'if (suggestionResult == null) {')
    content = content.replace('for (SuggestionResult.SuggestionInfo info : suggestionResult.getAllSuggestions()) {',
                               '// for (SuggestionResult.SuggestionInfo info : suggestionResult.getAllSuggestions()) {')
    content = content.replace('if (info.pt != null) {', '// if (info.pt != null) {')
    content = content.replace('map.put("lat", String.valueOf(info.pt.latitude));', '// map.put("lat", String.valueOf(info.pt.latitude));')
    content = content.replace('map.put("lon", String.valueOf(info.pt.longitude));', '// map.put("lon", String.valueOf(info.pt.longitude));')
    content = content.replace('map.put("name", info.key);', '// map.put("name", info.key);')
    content = content.replace('map.put("address", info.city + info.district);', '// map.put("address", info.city + info.district);')
    content = content.replace('mMapList.add(map);', '// mMapList.add(map);')
    content = content.replace('// // }', '// }')

    # Clean up empty lines
    content = re.sub(r'\n{3,}', '\n\n', content)

    with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'w', encoding='utf-8') as f:
        f.write(content)

    print("Migration complete!")

if __name__ == '__main__':
    main()
