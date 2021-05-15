package model.Users;

import javax.persistence.Entity;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.FetchType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import model.User;

@Entity
@Table(name = "Actvity_Forum")
public class ActivityForum {

    @Id
    @SequenceGenerator(name = "afcSeq", sequenceName = "ZSEQ_af_ID",allocationSize = 1,initialValue = 1)
    @GeneratedValue(generator = "afSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "categoryCount")
    private Long categoryCount = 0L;

    @Column(name = "topicCount")
    private Long topicCount = 0L;

    @Column(name = "potCount")
    private Long potCount = 0L;

    @Column(name = "answerCount")
    private Long answerCount = 0L;

    @OneToOne(mappedBy = "activityForum")
    private User user;

    public ActivityForum(){
    }

    public ActivityForum(Long categoryCount, Long topicCount, Long potCount, Long answerCount, User user) {
        this.categoryCount = categoryCount;
        this.topicCount = topicCount;
        this.potCount = potCount;
        this.answerCount = answerCount;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    //----Category
    public Long getCategoryCount() {
        return categoryCount;
    }

    public void incCategoryCount(){
        this.categoryCount ++;
    }

    public void decCategoryCount(){
        this.categoryCount --;
    }

    public void setCategoryCount(Long categoryCount) {
        this.categoryCount = categoryCount;
    }
    //----Topic
    public Long getTopicCount() {
        return topicCount;
    }

    public void incTopicCount(){
        this.topicCount ++;
    }

    public void decTopicCount(){
        this.topicCount --;
    }

    public void setTopicCount(Long topicCount) {
        this.topicCount = topicCount;
    }
    //----Post
    public Long getPotCount() {
        return potCount;
    }

    public void incPostCount(){
        this.potCount++;
    }

    public void decPostCount(){
        this.potCount --;
    }

    public void setPotCount(Long potCount) {
        this.potCount = potCount;
    }
    //----Answer
    public Long getAnswerCount() {
        return answerCount;
    }

    public void incAnswerCount(){
        this.answerCount++;
    }

    public void decAnswerCount(){
        this.answerCount --;
    }
    public void setAnswerCount(Long answerCount) {
        this.answerCount = answerCount;
    }
    
}
