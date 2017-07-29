package com.point.service.impl;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.point.entity.*;
import com.point.mongo.FictionDeatilRepository;
import com.point.mongo.UserFictionRepository;
import com.point.redis.UserFictionRedis;
import com.point.service.UserFictionService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hadoop on 2017-7-18.
 */

@Service
public class UserFictionServiceImpl implements UserFictionService {

    protected static Logger logger = LoggerFactory.getLogger(UserFictionServiceImpl.class);

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    UserFictionRedis userFictionRedis;

    @Autowired
    FictionDeatilRepository fictionDeatilRepository;


    @Override
    public List<UserFictionBean> getUserFictionList(String uid) {

        DBObject queryObject = new BasicDBObject();
        queryObject.put("uid", Long.parseLong(uid));

        DBObject fields = new BasicDBObject();
        fields.put("_id", false);
        fields.put("fiction_id", true);
        fields.put("fiction_name", true);
        fields.put("user_read_timestamp", true);


        List<UserFictionBean> userFictionBeanList = null;

        try {
            userFictionBeanList = mongoTemplate.find(new BasicQuery(queryObject, fields).with(new Sort(new Sort.Order(Sort.Direction.DESC, "user_read_timestamp"))).limit(20), UserFictionBean.class);
        } catch (Exception e) {
            logger.error("UserFictionServiceImpl,getUserFictionList is error uid={}", uid);
        }
        return userFictionBeanList;
    }

    @Override
    public boolean insertUserFictionToMongo(UserFictionBean userFictionBean) {

        try {
            mongoTemplate.upsert(new Query(Criteria.where("uid").is(userFictionBean.getUid()).and("fiction_id").is(userFictionBean.getFiction_id()).and("fiction_name").is(userFictionBean.getFiction_name())),
                    Update.update("user_read_timestamp", userFictionBean.getUser_read_timestamp()), userFictionBean.getClass());


            // userFictionRedis.removeUserFictionByUid(key);
            //  userFictionRedis.insertUserFictionBeanListToRedis(key,getUserFictionList(userFictionBean.getUid()));
            return true;
        } catch (Exception e) {
            logger.error("insertUserFictionToMongoAndRedis is exception,UserFictionBean={}", userFictionBean);
            return false;
        }

    }

    @Override
    public boolean redisUserFictionExists(String key) {
        return userFictionRedis.redisExist(key);
    }

    @Override
    public List<UserFictionBean> getUserFictionListFromRedis(String key) {
        return userFictionRedis.getUserFictionListByKey(key);
    }

    @Override
    public void insertUserFictionListToRedis(String key, List<UserFictionBean> userFictionBeans) {

        userFictionRedis.insertUserFictionList(key, userFictionBeans);
    }

    public List<Long> getUserReadFictionSetForRedis(String key) {
        List<Long> userReadFictionSet = userFictionRedis.getUserReadFictionSet(key);

        return userReadFictionSet;
    }

    public List<Long> getUserReadFictionSetForMongo(String key, String uid) {

        Query query = new Query(Criteria.where("uid").is(Long.parseLong(uid))).with(new Sort(new Sort.Order(Sort.Direction.DESC, "user_read_timestamp")));

        List<UserFictionBean> mongoList = mongoTemplate.find(query, UserFictionBean.class);

        List<Long> userReadFictionSet = new ArrayList<Long>();

        for (UserFictionBean userFictionBean : mongoList) {

            userReadFictionSet.add(userFictionBean.getFiction_id());

        }
        userFictionRedis.insertUserReadFictionSetToRedis(key, userReadFictionSet);

        return userReadFictionSet;

    }

    @Override
    public List<FictionDetailBean> getFictionDetailInfoByIdForRedis(String key, String fiction_id, String fiction_page_num) {

        List<FictionDetailBean> fictionDetailBeanList = userFictionRedis.getFictionDetailInfoByIdForRedis(key, fiction_id, fiction_page_num);

        return fictionDetailBeanList;
    }

    public List<FictionDetailBean> getFictionDetailInfoByIdForMongo(String fiction_id, String fiction_page_num,int page_num) {

        List<FictionDetailBean> fictionDetailBeanList = mongoTemplate.find(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id)).and("actor_fiction_detail_index").gt(Long.parseLong(fiction_page_num)*page_num).lte((Long.parseLong(fiction_page_num)+1)*page_num)),FictionDetailBean.class);

        return fictionDetailBeanList;
    }


    @Override
    public List<FictionBean> getMyFictionByUid(String uid) {

        List<FictionBean> fictionBeanList = mongoTemplate.find(new Query(Criteria.where("fiction_author_id").is(uid)).with(new Sort(new Sort.Order(Sort.Direction.DESC, "update_time"))), FictionBean.class);


        return fictionBeanList;
    }

    public boolean delMyFiction(String fiction_id, String uid) {

        try{
            mongoTemplate.remove(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id)).and("fiction_author_id").is(uid)), FictionBean.class);

            mongoTemplate.remove(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id))), FictionDetailBean.class);

            return true;
        }catch (Exception e){
            logger.error("delMyFiction is error,fiction={},uid={}",fiction_id,uid);
            return false;
        }


    }

    /**
     * @param fiction_id
     * @param fiction_detail_num
     */
    public List<FictionDetailBean> getFictionEndDeatil(String fiction_id, long fiction_detail_num) {

        List<FictionDetailBean> fictionDetailBeanList = mongoTemplate.find(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id)).and("actor_fiction_detail_index").gt(fiction_detail_num)), FictionDetailBean.class);

        return fictionDetailBeanList;

    }


    public List<FictionDetailBean> getFictionPreviousDetailFromMongo(String fiction_id, long start_fiction_detail_num, long end_fiction_detail_num) {

        List<FictionDetailBean> fictionDetailBeanList = mongoTemplate.find(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id)).and("actor_fiction_detail_index").gt(start_fiction_detail_num).lte(end_fiction_detail_num)).with(new Sort(new Sort.Order(Sort.Direction.ASC, "actor_fiction_detail_index"))), FictionDetailBean.class);

        return fictionDetailBeanList;
    }

    public String insertOneFictionDetail(FictionDetailBean fictionDetailBean) {
        FictionDetailBean fictionDetailMongoBean = fictionDeatilRepository.insert(fictionDetailBean);

        String id = fictionDetailMongoBean.getId();

        return id;
    }

    public boolean updateOneFictionDetail(String id, String actor_fiction_detail) {

        try {
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(new ObjectId(id))), Update.update("actor_fiction_detail", actor_fiction_detail), FictionDetailBean.class);
            return true;
        } catch (Exception e) {
            logger.error("updateOneFictionDetail is error,id={},actor_fiction_detail={} ", id, actor_fiction_detail);
            return false;
        }
    }

    public boolean delOneFictionDetail(String id) {

        try {
            fictionDeatilRepository.delete(id);
            return true;
        } catch (Exception e) {
            logger.error("delOneFictionDetail is error,id={}", id);
            return false;
        }
    }

    public boolean addActorintoFictionInfo(String fiction_id, String actor_name) {
        try {
            mongoTemplate.updateFirst(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id))), new Update().addToSet("fiction_actors", actor_name), FictionBean.class);
            return true;
        } catch (Exception e) {
            logger.error("delOneFictionDetail is error,fiction_id={},actor_name={}", fiction_id, actor_name);
            return false;
        }
    }

    public boolean delActorintoFictionInfo(String fiction_id, String actor_name){
        try {
      //  mongoTemplate.remove(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id)).and("fiction_actors").is(actor_name)),FictionBean.class);

            mongoTemplate.updateFirst(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id))),new Update().pull("fiction_actors", actor_name),FictionBean.class);


            return true;
        } catch (Exception e) {
            logger.error("delActorintoFictionInfo is error,fiction_id={},actor_name={}", fiction_id, actor_name);
            return false;
        }
    }

    public boolean releaseFictionDetail(String fiction_id){
        try {
            mongoTemplate.updateMulti(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id)).and("fiction_detail_status").is(0)), Update.update("fiction_detail_status", 1), FictionDetailBean.class);
            return true;
        } catch (Exception e) {
            logger.error("releaseFictionDetail is error,fiction_id={}", fiction_id);
            return false;
        }
    }

    public boolean actordetailisExists(String fiction_id,String actor_name){

        FictionDetailBean fictionDetailBean =  mongoTemplate.findOne(new Query(Criteria.where("fiction_id").is(Long.parseLong(fiction_id)).and("actor_name").is(actor_name)),FictionDetailBean.class);

        if(null!=fictionDetailBean){
            return false;
        }
        return true;
    }

}