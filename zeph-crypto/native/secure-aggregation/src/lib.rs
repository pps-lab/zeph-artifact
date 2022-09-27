use aes::Aes256;
use aes::Block;
use aes::cipher::BlockEncrypt;
use aes::cipher::KeyInit;
use aes::cipher::Key;

use std::collections::HashMap;
use std::collections::HashSet;
use multimap::MultiMap;
use byteorder::{ByteOrder, LittleEndian};

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JMap};
use jni::sys::{jlongArray, jlong, jint, jshort};


#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationNative_jniInit(
    _env: JNIEnv,
    _class: JClass,
    nfields: jlong,
) -> jlong {

    let secure_aggregation  = SecureAggregation::new(nfields as u64);
    Box::into_raw(Box::new(secure_aggregation)) as jlong
}

#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationNative_jniAddSharedKeys(
    env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
    map_object: JObject,
) {

    let secure_aggregation = &mut *(agg_ptr as *mut SecureAggregation);
    secure_aggregation.add_shared_keys(map_object, env);
}


#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationNative_jniBuildEpochNeighbourhoodsER(
    env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
    epoch: jlong,
    node_id: jlong,
    node_ids_arr: jlongArray,
    k: jint
) {
    let secure_aggregation = &mut *(agg_ptr as *mut SecureAggregation);

    let num_nodes = env.get_array_length(node_ids_arr).unwrap();
    let mut buff = vec![0; num_nodes as usize];
    env.get_long_array_region(node_ids_arr, 0, &mut buff).unwrap();

    secure_aggregation.build_epoch_neighbourhoods(epoch, node_id, &buff, k as u32, env);
}


#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationNative_jniClearEpochNeighbourhoodsER(
    _env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
    epoch: jlong,
    node_id: jlong
) {
    let secure_aggregation = &mut *(agg_ptr as *mut SecureAggregation);
    secure_aggregation.clear_epoch_neighbourhoods(epoch, node_id);
}

#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationNative_jniGetDummyKeySumER(
    env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
    timestamp: jlong,
    epoch: jlong,
    t: jshort,
    node_id: jlong,
    dropped_node_arr: jlongArray,
    callback: JObject
) {
    let secure_aggregation = &mut *(agg_ptr as *mut SecureAggregation);

    let num_dropped = env.get_array_length(dropped_node_arr).expect("failed to get length of dropped");
    let mut dropped_buff = vec![0; num_dropped as usize];
    env.get_long_array_region(dropped_node_arr, 0, &mut dropped_buff).expect("failed to copy dropped");

    secure_aggregation.get_dummy_key_sum_opt(timestamp, epoch, t as u16, node_id, &dropped_buff, env, callback);
}

#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationNative_jniGetDummyKeySum(
    env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
    timestamp: jlong,
    node_id: jlong,
    neighbour_id_arr: jlongArray,
    callback: JObject
) {
    let secure_aggregation = &mut *(agg_ptr as *mut SecureAggregation);

    let num_neighbours = env.get_array_length(neighbour_id_arr).unwrap();
    let mut neighbour_buff = vec![0; num_neighbours as usize];
    env.get_long_array_region(neighbour_id_arr, 0, &mut neighbour_buff).unwrap();

    secure_aggregation.get_dummy_key_sum(timestamp, node_id, &neighbour_buff, env, callback);
}


#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationNative_jniClose(
    _env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
) {
    let _boxed_secure_aggregation = Box::from_raw(agg_ptr as *mut SecureAggregation);
}

struct SecureAggregation {
    shared_keys:  HashMap<(i64, i64), Key<Aes256>>,
    neighbourhoods: HashMap<(i64, i64), MultiMap<u16, i64>>,
    n_fields: u64,
}

impl SecureAggregation {
    pub fn new(n_fields: u64) -> SecureAggregation {
        let secure_aggregation = SecureAggregation{
            shared_keys: HashMap::new(),
            neighbourhoods: HashMap::new(),
            n_fields: n_fields,
        };

        return secure_aggregation;
    }

    pub fn add_shared_keys(&mut self, map_object: JObject, env: JNIEnv){
        let map = JMap::from_env(&env, map_object).unwrap();

        for iter in map.iter(){
            for e in iter{
                let edge_str = String::from(env.get_string(e.0.into()).unwrap());
                let edge: Vec<i64> = edge_str.split("_").map(|s| s.parse().unwrap()).collect();

                let value = env.convert_byte_array(e.1.into_inner()).unwrap();
                let shared_key = Key::<Aes256>::clone_from_slice(&value);

                if edge[0] < edge[1]{
                    self.shared_keys.insert((edge[0], edge[1]), shared_key);
                }else if edge[0] > edge[1]{
                    self.shared_keys.insert((edge[1], edge[0]), shared_key);
                }
            }
        }
    }


    pub fn build_epoch_neighbourhoods(&mut self, epoch: i64, node_id: i64, node_ids: &[i64], k: u32, env: JNIEnv){


        let mut multi_map = self.neighbourhoods.entry((epoch, node_id)).or_insert(MultiMap::new());

        let mut epoch_bytes = [0; 16];
        let input = [0, epoch];
        LittleEndian::write_i64_into(&input, &mut epoch_bytes);

        // go over all node ids and apply prf and calculate partitioning
        for other_node_id in node_ids.into_iter() {
            let other_node_id = *other_node_id;
            if node_id < other_node_id {
                let shared_key = match self.shared_keys.get(&(node_id, other_node_id)){
                    Some(shared_key) => shared_key,
                    None => {
                        env.throw_new("java/lang/RuntimeException", &format!("missing shared keys(1): {:?}_{:?}    epoch={:?}  node_ids={:?}", node_id, other_node_id, epoch, node_ids)).unwrap();
                        return;
                    }
                };
                apply_and_parse(shared_key, &epoch_bytes, other_node_id, &mut multi_map, k);
            }else if other_node_id < node_id{
                let shared_key = match self.shared_keys.get(&(other_node_id, node_id)){
                    Some(shared_key) => shared_key,
                    None => {
                        env.throw_new("java/lang/RuntimeException", &format!("missing shared keys(2): {:?}_{:?}    epoch={:?}  node_ids={:?}", other_node_id, node_id, epoch, node_ids)).unwrap();
                        return;
                    }
                };

                apply_and_parse(shared_key, &epoch_bytes, other_node_id, &mut multi_map, k);
            }
        }

        //println!("built neighbourhoods: epoch={:?}  node_id={:?}", epoch, node_id);
    }

    pub fn clear_epoch_neighbourhoods(&mut self, epoch: i64, node_id: i64){
        self.neighbourhoods.remove(&(epoch, node_id));
        //println!("cleared neighbourhoods: epoch={:?}  node_id={:?}", epoch, node_id);
    }

    pub fn get_dummy_key_sum_opt(&mut self, timestamp: i64, epoch: i64, t: u16, node_id: i64, dropped_node_ids: &[i64], env: JNIEnv, callback: JObject){

        let multi_map =  match self.neighbourhoods.get(&(epoch, node_id)){
            Some(multi_map) => multi_map,
            None => {
                    env.throw_new("java/lang/RuntimeException", &format!("failed to get multimap: epoch={:?}  node_id={:?} t={:?}", epoch, node_id, t)).unwrap();
                    return;
                }
        };
        let neighbour_ids = match multi_map.get_vec(&t){
            Some(neighbour_ids) => neighbour_ids,
            None => {
                    env.throw_new("java/lang/RuntimeException", "failed to find t").unwrap();
                    return;
                }
        };
        get_dummy_key_sum(&self.shared_keys, timestamp, node_id, neighbour_ids, dropped_node_ids, self.n_fields, env, callback);
    }

    pub fn get_dummy_key_sum(&mut self, timestamp: i64, node_id: i64, neighbour_ids: &[i64], env: JNIEnv, callback: JObject){
        let dropped_node_ids: Vec<i64> = Vec::new();


        get_dummy_key_sum(&self.shared_keys, timestamp, node_id, neighbour_ids, &dropped_node_ids, self.n_fields, env, callback);
    }

}

fn update_aggregators(shared_key: &Key<Aes256>, w: i64, aggregators: &mut [i64], add: bool) {

    // Initialize cipher
    let cipher = Aes256::new(shared_key);

    for (i, aggregator) in aggregators.iter_mut().enumerate(){

        // Construct AES input block
        let mut bytes = [0; 16];

        let c = (i + 1) as i64;
        let input = [w, c];
        LittleEndian::write_i64_into(&input, &mut bytes);
        let mut block = Block::clone_from_slice(&bytes);

        // Encrypt block in-place
        cipher.encrypt_block(&mut block);

        // decode AES output to dummy key
        let mut output = [0; 2];
        LittleEndian::read_i64_into(&block, &mut output);
        let dummy_key = output[0] ^ output[1];

        if add {
            *aggregator = (*aggregator).wrapping_add(dummy_key);
        }else{
            *aggregator = (*aggregator).wrapping_sub(dummy_key);
        }
    }
}


fn get_dummy_key_sum(shared_keys: &HashMap<(i64, i64), Key<Aes256>>, timestamp: i64, node_id: i64, neighbour_ids: &[i64], dropped_node_ids: &[i64], n_fields: u64, env: JNIEnv, callback: JObject){
    //pub fn get_dummy_key_sum(&mut self, timestamp: i64, node_id: i64, neighbour_ids: &[i64], dropped_node_ids: &[i64], env: JNIEnv, callback: JObject){

        let dropped_nodes: HashSet<i64> = dropped_node_ids.into_iter().map(|v| *v).collect();

        let mut aggregators = vec![0; n_fields as usize];

        for neighbour_id in neighbour_ids.into_iter() {

            let neighbour_id = *neighbour_id;
            let dropped = dropped_nodes.contains(&neighbour_id);

            if !dropped {
                if node_id < neighbour_id {
                    let shared_key = shared_keys.get(&(node_id, neighbour_id)).unwrap();
                    update_aggregators(shared_key, timestamp, aggregators.as_mut_slice(), false);
                } else if neighbour_id < node_id {
                    let shared_key = shared_keys.get(&(neighbour_id, node_id)).unwrap();
                    update_aggregators(shared_key, timestamp, aggregators.as_mut_slice(), true);
                }
            }
        }

        let result_array = env.new_long_array(n_fields as i32).expect("cannot create result array");
        env.set_long_array_region(result_array, 0, &aggregators).expect("cannot copy results into jlong array");

        env.call_method(callback, "dummyKeySumCallback", "([J)V", &[result_array.into()]).unwrap();
    }

fn apply_and_parse(shared_key: &Key<Aes256>, epoch_bytes: &[u8], neighbour_id: i64, multi_map: &mut MultiMap<u16, i64>, k: u32){

    let cipher = Aes256::new(shared_key);
    let mut block = Block::clone_from_slice(&epoch_bytes);
    cipher.encrypt_block(&mut block);

    let mut parts = [0; 2];
    LittleEndian::read_u64_into(&block, &mut parts);

    let long_size = 64;

    let shift_count = long_size / k;

    let mask = u64::max_value().wrapping_shr(long_size - k);

    // println!("binary: {:#064b}   part0={:?}", parts[0] ,parts[0]);
    // println!("binary: {:#064b}   part1={:?}", parts[1] ,parts[1]);

    let shift_update = (2 << (k-1)) as u16;
    let mut shift = 0;

    for _i in 0..shift_count {
        // println!("binary: {:#064b}   part={:?}",parts[0] ,parts[0]);
        let t = (parts[0] & mask) as u16 + shift;
        // println!("binary: {:#016b}   t={:?}",t ,t);
        multi_map.insert(t, neighbour_id);
        parts[0] = parts[0].wrapping_shr(k);
        shift += shift_update;
    }

    for _i in 0..shift_count {
        let t = (parts[1] & mask) as u16 + shift;
        // println!("binary: {:#016b}   t={:?}",t ,t);
        multi_map.insert(t, neighbour_id);
        parts[1] = parts[1].wrapping_shr(k);
        shift += shift_update;
    }

    let remaining = long_size % k;
    if 2 * remaining >= k {
        parts[0] = parts[0].wrapping_shl(remaining);
        let combination = parts[0] | parts[1];
        let t = (combination & mask) as u16 + shift;
        // println!("binary: {:#016b}   t={:?}",t ,t);
        multi_map.insert(t, neighbour_id);
    }

}
