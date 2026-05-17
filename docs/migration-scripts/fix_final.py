# -*- coding: utf-8 -*-
with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix mGeoCoder
content = content.replace('mGeoCoder = GeoCoder.newInstance();', '// mGeoCoder removed')

# Fix duplicate comment and SuppressLint
content = content.replace(
    '    //开启地图的定位图层\n    @SuppressLint("MissingPermission")\n    //开启地图的定位图层\n    private void initMapLocation()',
    '    @SuppressLint("MissingPermission")\n    //开启地图的定位图层\n    private void initMapLocation()'
)

# Fix recordCurrentLocation - use gcj02towgs84 instead of bd2wgs
content = content.replace('double[] latLng = MapUtils.bd2wgs(lng, lat);',
                          'double[] latLng = MapUtils.gcj02towgs84(lng, lat);')
content = content.replace('//参数坐标系：bd09', '//参数坐标系：GCJ02')
content = content.replace('coordtype=bd09ll', 'coordtype=wgs84ll')

# Fix SuggestionSearch listener
content = content.replace('mSuggestionSearch.setOnGetSuggestionResultListener',
                          '// mSuggestionSearch.setOnGetSuggestionResultListener')

# Fix getMapList - replace entire broken method
old_getmaplist = '''    @NonNull
    private static List<Map<String, Object>> getMapList(Object suggestionResult) {
        List<Map<String, Object>> data = new ArrayList<>();
        int retCnt = null.size();

        for (int i = 0; i < retCnt; i++) {
            if (null.get(i).pt == null) {
                continue;
            }

            Map<String, Object> poiItem = new HashMap<>();
            poiItem.put(POI_NAME, null.get(i).key);
            poiItem.put(POI_ADDRESS, null.get(i).city + " " + null.get(i).district);
            poiItem.put(POI_LONGITUDE, "" + null.get(i).pt.longitude);
            poiItem.put(POI_LATITUDE, "" + null.get(i).pt.latitude);
            data.add(poiItem);
        }
        return data;
    }'''

new_getmaplist = '''    @NonNull
    private static List<Map<String, Object>> getMapList(Object suggestionResult) {
        // SuggestionSearch removed - using OkHttp web API
        return new ArrayList<>();
    }'''

content = content.replace(old_getmaplist, new_getmaplist)

with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'w', encoding='utf-8') as f:
    f.write(content)

print('Final fixes applied')
