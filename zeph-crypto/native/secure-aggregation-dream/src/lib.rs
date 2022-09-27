use aes::Aes256;
use aes::Block;
use aes::cipher::BlockEncrypt;
use aes::cipher::KeyInit;
use aes::cipher::Key;

use std::collections::HashMap;
use byteorder::{ByteOrder, LittleEndian};

use jni::JNIEnv;
use jni::objects::{JClass, JObject, JMap};
use jni::sys::{jlongArray, jlong, jint};


#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationDreamNative_jniInit(
    _env: JNIEnv,
    _class: JClass,
    nfields: jlong,
) -> jlong {

    let secure_aggregation  = SecureAggregationDream::new(nfields as u64);
    Box::into_raw(Box::new(secure_aggregation)) as jlong
}

#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationDreamNative_jniAddSharedKeys(
    env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
    map_object: JObject,
) {

    let secure_aggregation = &mut *(agg_ptr as *mut SecureAggregationDream);
    secure_aggregation.add_shared_keys(map_object, env);
}


#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationDreamNative_jniGetDummyKeySumDream(
    env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
    r1: jlong,
    r2: jlong,
    node_id: jlong,
    cluster_node_arr: jlongArray,
    k: jint,
    callback: JObject
) {
    let secure_aggregation = &mut *(agg_ptr as *mut SecureAggregationDream);

    let cluster_size = env.get_array_length(cluster_node_arr).unwrap();
    let mut cluster_buff = vec![0; cluster_size as usize];
    env.get_long_array_region(cluster_node_arr, 0, &mut cluster_buff).unwrap();

    secure_aggregation.get_dummy_key_sum_dream(r1, r2, node_id, &cluster_buff, k as u32, env, callback);
}

#[no_mangle]
pub unsafe extern "system" fn Java_ch_ethz_infk_pps_zeph_crypto_SecureAggregationDreamNative_jniClose(
    _env: JNIEnv,
    _class: JClass,
    agg_ptr: jlong,
) {
    let _boxed_secure_aggregation = Box::from_raw(agg_ptr as *mut SecureAggregationDream);
}

struct SecureAggregationDream {
    shared_keys:  HashMap<(i64, i64), Key<Aes256>>,
    n_fields: u64,
}

impl SecureAggregationDream {
    pub fn new(n_fields: u64) -> SecureAggregationDream {
        let secure_aggregation = SecureAggregationDream{
            shared_keys: HashMap::new(),
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

    pub fn get_dummy_key_sum_dream(&mut self, r1: i64, r2: i64, node_id: i64, cluster_node_ids: &[i64], k: u32, env: JNIEnv, callback: JObject){

        let mut aggregators = vec![0; self.n_fields as usize];

        let threshold = (1 as u128) <<(128-k);

        for neighbour_id in cluster_node_ids.into_iter() {
            let neighbour_id = *neighbour_id;
            if node_id < neighbour_id {
                let shared_key = self.shared_keys.get(&(node_id, neighbour_id)).unwrap();
                update_aggregators(shared_key, r1, r2, threshold, aggregators.as_mut_slice(), false);
            } else if neighbour_id < node_id {
                let shared_key = self.shared_keys.get(&(neighbour_id, node_id)).unwrap();
                update_aggregators(shared_key, r1, r2,  threshold, aggregators.as_mut_slice(), true);
            }

        }

        let result_array = env.new_long_array(self.n_fields as i32).expect("cannot create result array");
        env.set_long_array_region(result_array, 0, &aggregators).expect("cannot copy results into jlong array");

        env.call_method(callback, "dummyKeySumCallback", "([J)V", &[result_array.into()]).unwrap();

    }
}

fn update_aggregators(shared_key: &Key<Aes256>, r1: i64, r2: i64, threshold: u128, aggregators: &mut [i64], add: bool) -> bool{

    // Initialize cipher
    let cipher = Aes256::new(shared_key);

    let mut r1_bytes = [0; 16];
    let input = [0, r1];
    LittleEndian::write_i64_into(&input, &mut r1_bytes);


    let mut block = Block::clone_from_slice(&r1_bytes);
    cipher.encrypt_block(&mut block);


    let mut output = [0; 16];
    output.clone_from_slice(block.as_slice());

    let value = u128::from_le_bytes(output);

    if value < threshold {
        for (i, aggregator) in aggregators.iter_mut().enumerate(){

            // Construct AES input block
            let mut bytes = [0; 16];

            let c = (i + 1) as i64;
            let input = [r2, c];
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

        return true;
    }
    return false;
}
