package org.dog.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.awt.*;
import java.io.Serializable;

/**
 * @Author: Odin
 * @Date: 2023/7/26 10:26
 * @Description:
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class Stock implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String name;

    private Integer count;

    private Integer sale;

    private Integer version;

}
