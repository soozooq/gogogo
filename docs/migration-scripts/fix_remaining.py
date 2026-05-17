import re

with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Remove setOnMapTouchListener block
content = re.sub(
    r'mTencentMap\.setOnMapTouchListener\(event\s*->\s*\{[^}]*\}\);',
    '// setOnMapTouchListener not available in Tencent SDK 5.8.3',
    content
)

# Fix 2: Remove mGeoCoder.reverseGeoCode line in onMapLongClick
content = re.sub(
    r'mGeoCoder\.reverseGeoCode\(new\s+ReverseGeoCodeOption\(\)\.location\(point\)\);',
    '// GeoCoder removed - using OkHttp web API',
    content
)

# Fix 3: Remove setOnMapDoubleClickListener block
content = re.sub(
    r'mTencentMap\.setOnMapDoubleClickListener\(new\s+TencentMap\.OnMapDoubleClickListener\(\)\s*\{[^}]*\}\);',
    '// setOnMapDoubleClickListener not available in Tencent SDK 5.8.3',
    content
)

# Fix 4: Remove MyLocationData.Builder block in initMapLocation
content = re.sub(
    r'MyLocationData\s+\w+\s*=\s*new\s+MyLocationData\.Builder\(\)[^;]*;',
    '',
    content
)

# Fix 5: Remove MyLocationConfiguration line
content = re.sub(
    r'MyLocationConfiguration\s+\w+\s*=\s*new\s+MyLocationConfiguration\([^)]+\);',
    '',
    content
)

# Fix 6: Remove setMyLocationData line
content = re.sub(
    r'mTencentMap\.setMyLocationData\([^)]+\);',
    '',
    content
)

# Fix 7: Remove setMyLocationConfiguration line
content = re.sub(
    r'mTencentMap\.setMyLocationConfiguration\([^)]+\);',
    '',
    content
)

# Fix 8: Replace MapStatus.Builder in markMap
content = re.sub(
    r'MapStatus\.Builder',
    'CameraPosition.Builder',
    content
)

# Fix 9: Replace remaining LocationClient references
content = re.sub(r'mLocClient\.', '// mLocClient.', content)
content = re.sub(r'new\s+LocationClient\(', '// new LocationClient(', content)
content = re.sub(r'LocationClientOption', '// LocationClientOption', content)

# Fix 10: Replace remaining BDLocation references
content = re.sub(r'BDLocation\s+', 'Location ', content)
content = re.sub(r'BDLocation\.', 'Location.', content)
content = re.sub(r'BDAbstractLocationListener', 'LocationListener', content)

# Fix 11: Replace remaining SuggestionSearch references
content = re.sub(r'SuggestionSearchOption', 'Object', content)
content = re.sub(r'SuggestionResult', 'Object', content)

# Fix 12: Replace SearchResult
content = re.sub(r'SearchResult\.ERRORNO\.NO_ERROR', '0', content)

# Fix 13: Remove InfoWindow references
content = re.sub(r'InfoWindow', 'Object', content)

# Fix 14: Remove accuracy/direction/latitude/longitude builder chains
content = re.sub(r'\.accuracy\([^)]+\)', '', content)
content = re.sub(r'\.direction\([^)]+\)', '', content)
content = re.sub(r'\.latitude\([^)]+\)', '', content)
content = re.sub(r'\.longitude\([^)]+\)', '', content)
content = re.sub(r'\.build\(\)', '', content)

# Fix 15: Replace remaining MapStatus references
content = re.sub(r'MapStatus', 'CameraPosition', content)

# Fix 16: Replace remaining GeoCoder/ReverseGeoCode references
content = re.sub(r'GeoCoder', 'Object', content)
content = re.sub(r'ReverseGeoCodeOption', 'Object', content)
content = re.sub(r'ReverseGeoCodeResult', 'Object', content)
content = re.sub(r'GeoCodeResult', 'Object', content)
content = re.sub(r'OnGetGeoCoderResultListener', 'Object', content)

with open('app/src/main/java/com/zcshou/gogogo/MainActivity.java', 'w', encoding='utf-8') as f:
    f.write(content)

print('Remaining fixes applied')
