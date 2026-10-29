import argparse,json,re,hashlib,subprocess
from pathlib import Path
from preview_release import verify_container_main_hold
p=argparse.ArgumentParser();p.add_argument('--config',required=True);p.add_argument('--proof',required=True);p.add_argument('--raw',required=True);p.add_argument('--output',required=True);a=p.parse_args()
policy=json.loads(Path(a.config).read_text());a.round=policy['qualification_round'];a.job=policy['qualification_job_ids']['native']
assert re.fullmatch('[0-9a-f]{40}',policy['runtime_commit']) and type(a.round) is int and a.round>0 and type(a.job) is int and a.job>0
d=json.loads(Path(a.proof).read_text())
assert d['integrity_verified'] and d['commit']==policy['runtime_commit'] and d['run_id']==policy['qualification_run_id'], 'Native proof source/run differs from frozen policy'
n=d['native'];raw=Path(a.raw)/'cuttlefish-arm-boot/evidence';boot=json.loads((raw/'result.json').read_text())
assert d['integrity_verified'] and n['passed'] and n['source_count_verified']==9 and n['qemu_process_stopped'] and n['system_boot_passed']
assert boot['research_commit']==d['commit'] and boot['passed'] and boot['requested_stages_passed'] and boot['location_business_tested'] is False
prop=boot['properties'];assert prop['ro.build.version.sdk']=='33' and prop['ro.build.version.incremental']=='16596636' and prop['ro.product.cpu.abi']=='arm64-v8a' and prop['ro.dalvik.vm.native.bridge']=='0' and prop['ro.hw_timeout_multiplier']=='50'
assert boot['shell_uid']=='2000' and boot['selinux']=='Enforcing' and boot['guest_cpu_online']=='0-3' and boot['kernel_arch']=='aarch64' and boot['system_server_crash_pids']==[]
def junit(folder):
 t=(raw/folder/'junit.txt').read_text();m=re.search(r'^Time: ([0-9,]+(?:\.[0-9]+)?)\s*$',t,re.M);assert m and 'OK (1 test)' in t and 'FAILURES!!!' not in t
 return float(m[1].replace(',',''))
targets={};system_pids=set()
for key in ['wechat','douyin']:
 v=n['container_targets'][key];o=v['observed'];c=o['closed_startup_observation'];s=o['virtual_startup_observation'];original=n['ordinary_originals'][key]
 assert v['passed'] and o['passed'] and o['original_signer'] and o['closed_startup_confirmed'] and o['location_enabled'] is True and c['passed'] and c['input_pattern']==0
 assert s['passed'] and s['input_pattern']==2 and s['previous_main_absent'] and s['returned_to_host_before_stop'] and s['synthetic_input_only'] and s['target_location_business_tested'] is False
 for field in ['previous_main_pid','main_pid','main_uid']:assert type(s[field]) is int and s[field]>0
 assert s['main_uid']>=10000 and s['main_pid']!=s['previous_main_pid']
 if policy.get('requires_retired_process_fixture'):
  snapshot=o['before_cleanup_process_snapshot']
  assert snapshot['scope']=='READ_ONLY_BEFORE_STOP_OR_UNINSTALL_NOT_LOCATION_ADOPTION' and 'unavailable_error_class' not in snapshot
  assert snapshot['actual_host_uid']==s['main_uid'] and 0<len(snapshot['actual_host_uid_processes_max16'])<=16
  registered=[v for v in snapshot['registered_guest_processes'] if v['process_name']==o['package']]
  assert len(registered)==1 and registered[0]['pid']==s['main_pid'] and registered[0]['uid']==s['main_uid']
  assert any(v['pid']==s['main_pid'] and v['uid']==s['main_uid'] for v in snapshot['actual_host_uid_processes_max16'])
 assert v['terms_accepted'] is False and v['login_attempted'] is False and v['messages_sent'] is False and all(v['owned_package_cleanup'].values())
 assert original['passed'] and original['owned_package_removed'] and all(original['owned_ui_package_cleanup'].values())
 assert original['terms_accepted'] is False and original['login_attempted'] is False and original['messages_sent'] is False
 before=v['environment_before_container']['system_pid'];after=v['environment_after_container']['system_pid'];assert before==after;system_pids.update([before,after,original['system_server_pid_at_start']])
 crash=(raw/'container-target-startups'/key/'crash-log.txt').stat().st_size
 target={'passed':True,'junit_seconds':junit('container-target-startups/'+key),'closed_startup':c,'virtual_startup':s,'both_modes_anonymous_ui_confirmed':True,'previous_main_actually_absent_before_restart':True,'new_main_has_distinct_pid_and_real_host_uid':True,'location_business_tested':False,'terms_accepted':False,'login_attempted':False,'messages_sent':False,'captured_crash_log_bytes':crash,'png_available':any((raw/'container-target-startups'/key).glob('*.png')),'png_reviewed':False,'owned_package_cleanup':v['owned_package_cleanup']}
 if key=='douyin':
  h=o['post_ui_observation'];oh=original['post_ui_observation'];verify_container_main_hold(h,s,'com.ss.android.ugc.aweme');assert o['post_hold_privacy_ui_visible']
  assert oh['passed'] and oh['requested_seconds']==300 and oh['observed_seconds']>=300
  target['closed_mode_only_300s_hold']=h;target['ordinary_original_300s_hold']=oh
  scope='AMS_REGISTERED_MAIN_AND_KERNEL_PID_UID_NOT_PIDOF_SET'
  assert oh['identity_scope']==scope and oh['initial_target_pid']==oh['last_target_pid']
  uid=oh['initial_target_uid'];main=int(oh['initial_target_pid']);assert type(uid) is int and 10000<=uid<99000 and oh['last_target_uid']==uid
  trace=raw/'original-target-baselines/douyin/main-process-snapshots.jsonl';trace_bytes=trace.read_bytes()
  snapshots=[json.loads(line) for line in trace_bytes.decode().splitlines()]
  assert 3<=len(snapshots)<=256 and len(snapshots)==original['original_main_identity_check_count']
  assert not original.get('original_main_identity_history_truncated',False)
  assert snapshots[0]==oh['initial_main_identity'] and snapshots[-1]==original['last_original_main_identity']
  assert snapshots[0]['phase']=='AT_UI' and snapshots[-1]['phase']=='AFTER_POST_UI_OBSERVATION'
  assert any(x['phase']=='DURING_POST_UI_OBSERVATION' for x in snapshots)
  for snapshot in snapshots:
   assert snapshot['confirmed'] and snapshot['identity_scope']==scope
   assert snapshot['pid']==main and snapshot['uid']==snapshot['expected_uid']==uid
   assert len(snapshot['pidof_candidates'])<=32 and len(snapshot['ams_record_headers'])<=16
   names=[]
   for header in snapshot['ams_record_headers']:
    m=re.fullmatch(r'\*APP\* UID ([0-9]+) ProcessRecord\{[0-9a-fA-F]+ ([1-9][0-9]*):([^/\s{}]+)/([^\s{}]+)\}',header)
    if m and m[3]=='com.ss.android.ugc.aweme' and int(m[1])==uid:
     assert m[4]=='u0a'+str(uid-10000);names.append(int(m[2]))
   assert set(names)=={main}
  target['ordinary_main_role_identity']={'scope':scope,'main_pid':main,'real_uid':uid,'checks':len(snapshots),'observed_seconds':oh['observed_seconds'],'main_identity_confirmed_by_live_kernel_each_check':True,'additional_same_name_candidates_observed':any(len(x['pidof_candidates'])>1 for x in snapshots),'bounded_jsonl_sha256':hashlib.sha256(trace_bytes).hexdigest(),'full_ams_dump_copied':False}

 targets[key]=target
own=n['owned_location'];assert own['passed'] and own['own_staging_removed'] and all(own['owned_package_cleanup'].values())
framework=own['cases']['framework'];loc=own['cases']['location'];assert framework['passed'] and loc['passed']
o=loc['observed'];assert o['passed'] and o['report_source']=='OWN_GUEST_ATOMIC_PRIVATE_FILE' and [s['name'] for s in o['stages']]==['TARGET_A','TARGET_B','UNREGISTERED_STABLE','CURRENT_CANCELLED']
ages=[]
for stage in o['stages'][:2]:
 r=stage['guest_report'];assert r['gnss_satellite_count']==0 and r['gnss_first_fix_count']==0
 for field in ['last_gps','last_network','stream_gps','stream_network','current_gps','current_network','last_passive','current_passive','stream_passive']:
  sample=r[field];assert sample['present'] and sample['mock'] is True and sample['synthetic'] is True and sample['extra_satellites']==0
  assert sample['provider']==('network' if field.endswith('network') else 'gps')
  age=(r['report_elapsed_ns']-sample['elapsed_ns'])/1_000_000;assert 0<=age<=30000;ages.append(age)
assert o['stages'][-1]['guest_report']['cancelled_callbacks']==0
for v in own['cases'].values():system_pids.add(v['environment_after']['system_pid'])
system_pids.add(own['environment_before']['system_pid']);assert len(system_pids)==1
assert d['commit']==policy['runtime_commit'] and d['run_id']==policy['qualification_run_id'] and boot['research_commit']==policy['runtime_commit']
assert set(d['artifacts'])=={'own','linux','mac','native'} and len(d['default_cases'])==8 and all(v['passed'] for v in d['default_cases'].values())
assert d['feedback_main']['passed'] and d['feedback_main']['junit']=={'tests':447,'failures':0,'errors':0,'skipped':0}
expected_sources=policy.get('expected_own_source_count',59)
assert type(expected_sources) is int and 1<=expected_sources<=256
assert d['own_transport']['source_count_verified']==expected_sources and d['own_transport']['notice_materials_per_apk']==11 and d['own_transport']['signatures_verified_and_same']
assert set(own['cases'])=={'framework','location','presentation-contract'} and own['consumer_system_installed'] is False and own['third_party_accounts_used'] is False and own['target_location_business_tested'] is False
if policy.get('requires_retired_process_fixture'):
 fixture=framework['observed']['host_manager_stale_death_fixture']
 assert fixture=={'scope':'CONTROLLED_HOST_REGISTRY_NOT_REAL_GUEST_DEATH','replacement_same_name_preserved':True,'replacement_slot_files_preserved':True,'absent_old_registration_is_inert':True,'passed':True}
contract=own['cases']['presentation-contract'];co=contract['observed'];assert contract['passed'] and co=={'scope':'CONTROLLED_PUBLIC_API33_PACKAGE_FIXTURE_NOT_REAL_WECHAT_OR_SDK','passed':True,'expected_test_methods':7,'evidence_source':'EXACT_SOURCE_CONTRACT_CLASS_JUNIT_TRANSCRIPT'}
ct=(raw/'owned-location-native/presentation-contract/junit.txt').read_text()
assert re.findall(r'^OK \((\d+) tests?\)$',ct,re.M)==['7'] and not re.search('FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed|INSTRUMENTATION_TIMEOUT',ct)
assert re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)$',ct,re.M)==['-1']
cs=subprocess.check_output(['git','show',d['commit']+':research/container-api33/probe-host/src/androidTest/java/top/niunaijun/bcore/fake/service/LocationContractInstrumentedTest.java'],text=True)
expected_methods=re.findall(r'@Test\s+public void (\w+)\(',cs);actual_methods=re.findall(r'^INSTRUMENTATION_STATUS: test=(\w+)\nINSTRUMENTATION_STATUS_CODE: 0$',ct,re.M)
assert len(expected_methods)==7 and len(actual_methods)==7 and set(actual_methods)==set(expected_methods) and 'wechatPresentationKeepsSyntheticOriginAcrossLastCurrentAndStreams' in actual_methods and 'passiveRequestsForwardSyntheticGpsOriginAndKeepSeparateCounters' in actual_methods
cm=re.search(r'^Time: ([0-9,.]+)$',ct,re.M);assert cm;contract_seconds=float(cm[1].replace(',',''))
assert (raw/'owned-location-native/presentation-contract/crash-log.txt').stat().st_size==0
delivery=o['consumer_delivery'];assert delivery['scope']=='CORE_MEMORY_VIRTUAL_PREPARATION_AND_BINDER_SEND_NOT_SDK_ADOPTION' and delivery['present'] and delivery['maximum_profiles']==16
assert delivery['last_preparation_caller_pid']==delivery['last_stream_receiver_pid']==o['guest_pid'] and o['host_pid']!=o['guest_pid']
assert delivery['last_stream_mock'] is True and delivery['last_stream_synthetic'] is True
for provider in ['gps','network','passive']:
 assert delivery['cache_'+provider+'_prepared']>=2 and delivery['current_'+provider+'_prepared']>=2
 assert delivery['stream_'+provider+'_registered']>=1 and delivery['stream_'+provider+'_transaction_returned']>=2
assert all('latitude' not in key and 'longitude' not in key for key in delivery)
patch=subprocess.check_output(['git','show',d['commit']+':research/container-api33/patches/blackbox-api33-build.patch'])
result={'schema':'GOGOGO_NATIVE_CONSUMER_DELIVERY_PASS_RESULTS_V1','scope':'ACTUAL_NATIVE_ARM64_ANONYMOUS_STARTUP_GENERIC_GUEST_AND_CONTROLLED_WECHAT_PACKAGE_PRESENTATION_NOT_SDK_OR_PHONE_ADOPTION','round':a.round,'run_id':d['run_id'],'commit':d['commit'],'job_id':a.job,'conclusion':'success','engine_patch_sha256':hashlib.sha256(patch).hexdigest(),'artifacts':d['artifacts'],'all_four_api_digests_fixed_sources_and_own_apks_verified':True,'native_source_count_verified':9,'same_run_own_transport':d['own_transport'],'qualified_environment':{'properties':prop,'kernel_arch':boot['kernel_arch'],'shell_uid':boot['shell_uid'],'selinux':boot['selinux'],'guest_cpu_online':boot['guest_cpu_online'],'system_server_crash_pids':boot['system_server_crash_pids']},'system_server_pid_before_and_after':next(iter(system_pids)),'targets':targets,'owned_framework':{'passed':True,'junit_seconds':junit('owned-location-native/framework'),'observed':framework['observed'],'captured_crash_log_bytes':(raw/'owned-location-native/framework/crash-log.txt').stat().st_size},'owned_location':{'passed':True,'junit_seconds':junit('owned-location-native/location'),'host_pid':o['host_pid'],'guest_pid':o['guest_pid'],'report_source':o['report_source'],'stage_names':[s['name'] for s in o['stages']],'all_nine_paths_preserve_mock':True,'maximum_sample_age_at_record_ms':max(ages),'gnss_satellites_and_first_fix':0,'cancelled_callbacks':0,'own_staging_removed':own['own_staging_removed'],'package_cleanup':own['owned_package_cleanup']},'qemu_process_stopped':True,'whole_observed_seconds_not_app_startup_time':boot['observed_seconds'],'network_scope':boot['network_scope'],'terms_login_messages_target_location_tested':False,'real_modem_sdk_location_account_and_vivo':'NOT_VERIFIED','scope_limits':['New synthetic mode only proves anonymous startup, not target SDK location adoption.','The Douyin 300s hold is in closed mode; no 300s own-mode hold is claimed.','Owned Guest shares real host UID; functional positives do not prove permission denial or isolation.','No target screenshots were reviewed.','Native result is bound to this exact commit; another feedback UI source requires its own matched native proof.'],'verifier_sha256':d['verifier_sha256'],'inputs_manifest_sha256':d['inputs_manifest_sha256']}
result['controlled_presentation_contract']={'passed':True,'junit_methods':7,'junit_seconds':contract_seconds,'method_names':actual_methods,'observed_scope':co['scope'],'evidence_source':co['evidence_source'],'actual_wechat_process_or_sdk_executed':False,'synthetic_origin_retained':True,'both_framework_mock_aliases_for_wechat':False,'generic_mock_marked_after_restoration':True,'physical_base_delegations':0,'captured_crash_log_bytes':0,'transcript_sha256':hashlib.sha256(ct.encode()).hexdigest()}
result['all_three_owned_native_cases_passed']=True
result['qualification_driver_commit']=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
result['qualification_driver_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
result['phone_candidate_app_source']=policy['runtime_commit']
result['previous_ci53_failure_retained_not_reclassified']=True
result['previous_ci55_fixture_failure_retained_not_reclassified']=True
result['passive_samples_keep_virtual_gps_source_and_synthetic_origin']=True
result['passive_request_counters_separate_from_source_provider']=True
result['scope_limits'].append('Ordinary main identity is AMS role plus real kernel PID/UID; whole pidof sets are auxiliary and all bounded snapshots are checked. Stable anonymous main identity is not location adoption.')
result['scope_limits'].append('The seven-method compatibility contract uses a controlled package context. It does not execute the actual WeChat SDK or prove adoption of coordinates.')
result['actual_separate_guest_consumer_delivery']=delivery
if policy.get('requires_retired_process_fixture'):
 result['controlled_retired_process_registry']=fixture
 result['target_live_main_snapshots_checked_before_cleanup']=True
result['scope_limits'].append('Consumer counters are volatile Core history; prepared samples and returned Binder calls do not prove actual SDK adoption or the current application session.')
out=Path(a.output);assert not out.exists();out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'round':a.round,'commit':d['commit'],'targets':{k:{'seconds':v['junit_seconds'],'closed_pid':v['closed_startup']['guest_pid'],'new_pid':v['virtual_startup']['main_pid'],'uid':v['virtual_startup']['main_uid']} for k,v in targets.items()},'framework_seconds':result['owned_framework']['junit_seconds'],'nine_path_seconds':result['owned_location']['junit_seconds'],'system_server':result['system_server_pid_before_and_after'],'whole_seconds':boot['observed_seconds']}))
