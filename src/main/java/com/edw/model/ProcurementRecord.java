package com.edw.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <pre>
 *  com.edw.model.ProcurementRecord
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 28 Apr 2026 15:49
 */
@Entity
@Table(name = "procurement_record")
public class ProcurementRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "id_rup", nullable = false, unique = true)
    public String idRup;

    @Column(columnDefinition = "text", nullable = false)
    public String title;

    @Column(nullable = false)
    public BigDecimal budget;

    @Column(nullable = false)
    public Integer year;

    @Column(name = "id_satker")
    public String idSatker;

    @Column(name = "satker_name")
    public String satkerName;

    @Column(name = "id_klpd")
    public String idKlpd;

    public String institution;

    @Column(name = "klpd_type")
    public String klpdType;

    public String category;

    public Boolean embedded = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    public LocalDateTime createdAt;

    public ProcurementRecord() {
    }

    public ProcurementRecord(Long id, String idRup, String title, BigDecimal budget, Integer year, String idSatker, String satkerName, String idKlpd, String institution, String klpdType, String category, Boolean embedded, LocalDateTime createdAt) {
        this.id = id;
        this.idRup = idRup;
        this.title = title;
        this.budget = budget;
        this.year = year;
        this.idSatker = idSatker;
        this.satkerName = satkerName;
        this.idKlpd = idKlpd;
        this.institution = institution;
        this.klpdType = klpdType;
        this.category = category;
        this.embedded = embedded;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdRup() {
        return idRup;
    }

    public void setIdRup(String idRup) {
        this.idRup = idRup;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getIdSatker() {
        return idSatker;
    }

    public void setIdSatker(String idSatker) {
        this.idSatker = idSatker;
    }

    public String getSatkerName() {
        return satkerName;
    }

    public void setSatkerName(String satkerName) {
        this.satkerName = satkerName;
    }

    public String getIdKlpd() {
        return idKlpd;
    }

    public void setIdKlpd(String idKlpd) {
        this.idKlpd = idKlpd;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getKlpdType() {
        return klpdType;
    }

    public void setKlpdType(String klpdType) {
        this.klpdType = klpdType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getEmbedded() {
        return embedded;
    }

    public void setEmbedded(Boolean embedded) {
        this.embedded = embedded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
