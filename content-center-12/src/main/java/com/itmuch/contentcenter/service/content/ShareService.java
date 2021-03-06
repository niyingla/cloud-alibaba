package com.itmuch.contentcenter.service.content;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.itmuch.contentcenter.dao.content.MidUserShareMapper;
import com.itmuch.contentcenter.dao.content.ShareMapper;
import com.itmuch.contentcenter.dao.messaging.RocketmqTransactionLogMapper;
import com.itmuch.contentcenter.domain.dto.content.ShareAuditDTO;
import com.itmuch.contentcenter.domain.dto.content.ShareDTO;
import com.itmuch.contentcenter.domain.dto.messaging.UserAddBonusMsgDTO;
import com.itmuch.contentcenter.domain.dto.user.UserAddBonseDTO;
import com.itmuch.contentcenter.domain.dto.user.UserDTO;
import com.itmuch.contentcenter.domain.entity.content.MidUserShare;
import com.itmuch.contentcenter.domain.entity.content.Share;
import com.itmuch.contentcenter.domain.entity.messaging.RocketmqTransactionLog;
import com.itmuch.contentcenter.domain.enums.AuditStatusEnum;
import com.itmuch.contentcenter.feignclient.UserCenterFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ShareService {
    private final ShareMapper shareMapper;
    private final UserCenterFeignClient userCenterFeignClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketmqTransactionLogMapper rocketmqTransactionLogMapper;
    private final Source source;
    private final MidUserShareMapper midUserShareMapper;

    public ShareDTO findById(Integer id) {
        // ??????????????????
        Share share = this.shareMapper.selectByPrimaryKey(id);
        // ?????????id
        Integer userId = share.getUserId();

        // 1. ???????????????
        // 2. ?????????url???????????????https://user-center/s?ie={ie}&f={f}&rsv_bp=1&rsv_idx=1&tn=baidu&wd=a&rsv_pq=c86459bd002cfbaa&rsv_t=edb19hb%2BvO%2BTySu8dtmbl%2F9dCK%2FIgdyUX%2BxuFYuE0G08aHH5FkeP3n3BXxw&rqlang=cn&rsv_enter=1&rsv_sug3=1&rsv_sug2=0&inputT=611&rsv_sug4=611
        // 3. ??????????????????????????????????????????????????????
        // 4. ?????????????????????
        UserDTO userDTO = this.userCenterFeignClient.findById(userId);

        ShareDTO shareDTO = new ShareDTO();
        // ???????????????
        BeanUtils.copyProperties(share, shareDTO);
        shareDTO.setWxNickname(userDTO.getWxNickname());
        return shareDTO;
    }

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();
        // ???HTTP GET??????????????????????????????????????????
        ResponseEntity<String> forEntity = restTemplate.getForEntity(
            "http://localhost:8080/users/{id}",
            String.class, 2
        );

        System.out.println(forEntity.getBody());
        // 200 OK
        // 500
        // 502 bad gateway...
        System.out.println(forEntity.getStatusCode());
    }

    public Share auditById(Integer id, ShareAuditDTO auditDTO) {
        // 1. ??????share???????????????????????????????????????audit_status != NOT_YET??????????????????
        Share share = this.shareMapper.selectByPrimaryKey(id);
        if (share == null) {
            throw new IllegalArgumentException("????????????????????????????????????");
        }
        if (!Objects.equals("NOT_YET", share.getAuditStatus())) {
            throw new IllegalArgumentException("????????????????????????????????????????????????????????????");
        }

        // 3. ?????????PASS????????????????????????rocketmq?????????????????????????????????????????????????????????
        if (AuditStatusEnum.PASS.equals(auditDTO.getAuditStatusEnum())) {
            // ?????????????????????
            String transactionId = UUID.randomUUID().toString();

            this.source.output()
                .send(
                    MessageBuilder
                        .withPayload(
                            UserAddBonusMsgDTO.builder()
                                .userId(share.getUserId())
                                .bonus(50)
                                .build()
                        )
                        // header????????????...
                        .setHeader(RocketMQHeaders.TRANSACTION_ID, transactionId)
                        .setHeader("share_id", id)
                        .setHeader("dto", JSON.toJSONString(auditDTO))
                        .build()
                );
        } else {
            this.auditByIdInDB(id, auditDTO);
        }
        return share;
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditByIdInDB(Integer id, ShareAuditDTO auditDTO) {
        Share share = Share.builder()
            .id(id)
            .auditStatus(auditDTO.getAuditStatusEnum().toString())
            .reason(auditDTO.getReason())
            .build();
        this.shareMapper.updateByPrimaryKeySelective(share);

        // 4. ???share????????????
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditByIdWithRocketMqLog(Integer id, ShareAuditDTO auditDTO, String transactionId) {
        this.auditByIdInDB(id, auditDTO);

        this.rocketmqTransactionLogMapper.insertSelective(
            RocketmqTransactionLog.builder()
                .transactionId(transactionId)
                .log("????????????...")
                .build()
        );
    }

    public PageInfo<Share> q(String title, Integer pageNo, Integer pageSize, Integer userId) {
        PageHelper.startPage(pageNo, pageSize);
        List<Share> shares = this.shareMapper.selectByParam(title);
        List<Share> sharesDeal;
        // 1. ??????????????????????????????downloadUrl????????????null
        if (userId == null) {
            sharesDeal = shares.stream()
                .peek(share -> {
                    share.setDownloadUrl(null);
                })
                .collect(Collectors.toList());
        }
        // 2. ??????????????????????????????????????????mid_user_share????????????????????????????????????share???downloadUrl?????????null
        else {
            sharesDeal = shares.stream()
                .peek(share -> {
                    MidUserShare midUserShare = this.midUserShareMapper.selectOne(
                        MidUserShare.builder()
                            .userId(userId)
                            .shareId(share.getId())
                            .build()
                    );
                    if (midUserShare == null) {
                        share.setDownloadUrl(null);
                    }
                })
                .collect(Collectors.toList());
        }
        return new PageInfo<>(sharesDeal);
    }

    public Share exchangeById(Integer id, HttpServletRequest request) {
        Object userId = request.getAttribute("id");
        Integer integerUserId = (Integer) userId;

        // 1. ??????id??????share?????????????????????
        Share share = this.shareMapper.selectByPrimaryKey(id);
        if (share == null) {
            throw new IllegalArgumentException("?????????????????????");
        }
        Integer price = share.getPrice();

        // 2. ????????????????????????????????????????????????????????????
        MidUserShare midUserShare = this.midUserShareMapper.selectOne(
            MidUserShare.builder()
                .shareId(id)
                .userId(integerUserId)
                .build()
        );
        if (midUserShare != null) {
            return share;
        }

        // 3. ???????????????????????????id????????????????????????
        UserDTO userDTO = this.userCenterFeignClient.findById(integerUserId);
        if (price > userDTO.getBonus()) {
            throw new IllegalArgumentException("????????????????????????");
        }

        // 4. ???????????? & ???mid_user_share?????????????????????
        this.userCenterFeignClient.addBonus(
            UserAddBonseDTO.builder()
                .userId(integerUserId)
                .bonus(0 - price)
                .build()
        );
        this.midUserShareMapper.insert(
            MidUserShare.builder()
                .userId(integerUserId)
                .shareId(id)
                .build()
        );
        return share;
    }
}

