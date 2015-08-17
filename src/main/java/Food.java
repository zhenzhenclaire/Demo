import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by admin on 2015/8/17.
 */
public class Food {
    String ipAdress = "127.0.0.1";
    Jedis jedis;
    File f;
    String userID = "u1";
    int typeNum = 0;
    int numOfFood = 0;
    int needFood = 3;

    ArrayList<String> mostResult = new ArrayList<String>();
    ArrayList<String> rareResult = new ArrayList<String>();
    ArrayList<String> typeResult = new ArrayList<String>();
    HashMap<String,Integer> product = new HashMap<String, Integer>();
    HashMap<String,Boolean> typeMap = new HashMap<String, Boolean>();
    HashMap<String,Boolean> componentMap = new HashMap<String, Boolean>();

    Set<String> allType = new HashSet<String>();

    public void config(){
        initType();
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxIdle(5);

        config.setTestOnBorrow(false);

        JedisPool jedisPool = new JedisPool(config,this.ipAdress,6379);
        this.jedis = jedisPool.getResource();
    }

    public void saveProduct() throws IOException {
        f = new File("food");
        BufferedReader reader = new BufferedReader(new FileReader(f));
        String line = "";
        while((line = reader.readLine()) != null){
            String[] content = line.split(":");

            String pid = content[0];
            String component = content[1];
            String type = content[2];

            jedis.hset(pid, "component", component);
            jedis.hset(pid, "type", type);

            String components[] = component.split(",");
            for(String str:components){
                jedis.sadd(str,pid);
            }
            numOfFood++;
        }
    }

    public void saveReqToDB(String user,String time,String req){
        jedis.hset(user, "time", time);
        jedis.hset(user, "component", req);
    }

    public HashMap<String,Integer> countProduct(){
        HashMap<String,Integer> hm = new HashMap<String, Integer>();
        String[] component = jedis.hget(userID, "component").split(":");
//        System.out.print(component.length);
        for(String str:component) {
            componentMap.put(str,true);
            Set<String> smembers = jedis.smembers(str);
            for (String s : smembers) {
                if (hm.get(s) == null) {
                    hm.put(s, 1);
                } else {
                    hm.put(s, hm.get(s) + 1);
                }
            }
        }
        return hm;
    }

    public int addMost(HashMap<String,Integer> productCount){
        int max = 0;

        for(Map.Entry<String,Integer> entry : productCount.entrySet()){
            int val = entry.getValue();
            if(val > max) {
                max = val;
            }
        }

        for(Map.Entry<String,Integer> entry : productCount.entrySet()){
            int val = entry.getValue();
            if(val == max) {
                String key = entry.getKey();
                mostResult.add(key);
                this.numOfFood ++;

                String component = jedis.hget(key,"component");
                String[] c = component.split(",");
                for(String str:c){
                    if(componentMap.get(str) != null && componentMap.get(str)){
                        componentMap.put(str,false);
                    }
                }

                String type = jedis.hget(key,"type");
                allType.remove(type);
//                String type = jedis.hget(key,"type");
//                typeMap.put(type,false);
            }
        }
        return max;
    }

    public void addRare(){
        for(Map.Entry<String,Boolean> entry:componentMap.entrySet()){
            if(entry.getValue()){
                String componentLeft = (String) entry.getKey();
                Set<String> smembers = jedis.smembers(componentLeft);
                for(String str:smembers){
                    rareResult.add(str);
                    numOfFood ++;
                    String type = jedis.hget(str,"type");
                    allType.remove(type);
                }
            }
        }
    }

    public void addType(){
        int max = 0;
        String res = null;
        String resType = null;
        for(Map.Entry<String,Integer> entry : this.product.entrySet()){
            String key = entry.getKey();
            int val = entry.getValue();
            String type = jedis.hget(key, "type");
            if(val > max && allType.contains(type)){
                max = val;
                res = key;
                resType = type;
            }
        }
        typeResult.add(res);
        numOfFood++;
        allType.remove(resType);
    }



    public static  void main(String[] args) throws IOException {
        Food food = new Food();

        //Config Redis;
        food.config();

        //Add product to Redis;
        //food.saveProduct();

        //Save requirement to Redis;
        String requireString = "A:B:F";
        food.saveReqToDB(food.userID,"09:50",requireString);

        //Count num of product
        HashMap<String,Integer> productCount = new HashMap<String, Integer>();
        productCount = food.countProduct();
        food.product = productCount;

        //Add most products
        int maxtimes = food.addMost(productCount);

        if(maxtimes == requireString.split(":").length){
            food.addType();
        }else if(food.needFood > food.numOfFood){
            //Find rare product
            food.addRare();
            if(food.needFood > food.numOfFood){
                food.addType();
            }
        }else{

        }
        System.out.println("most:"+food.mostResult);
        System.out.println("rare:"+food.rareResult);
        System.out.println("type:"+food.typeResult);




    }
    public void initType(){
        allType.add("Fruit");
        allType.add("Drink");
        allType.add("Cake");
    }
}
