package com.ecodispatch.repository;

import com.ecodispatch.model.DistrictAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DistrictAlertRepository — Spring Data JPA repository for {@link DistrictAlert}.
 *
 * <p>Provides out-of-the-box CRUD operations plus custom query methods
 * used by the scheduler and REST controller.</p>
 */
@Repository
public interface DistrictAlertRepository extends JpaRepository<DistrictAlert, Long> {

    /**
     * Retrieves all district alerts that are NOT in NORMAL status.
     * Used by the dashboard API to surface only actionable anomaly records.
     *
     * @param status the status value to exclude (pass "NORMAL")
     * @return list of active anomaly records
     */
    List<DistrictAlert> findByStatusNot(String status);

    /**
     * Retrieves all alerts for a specific district (case-sensitive).
     */
    List<DistrictAlert> findByDistrictName(String districtName);
}
