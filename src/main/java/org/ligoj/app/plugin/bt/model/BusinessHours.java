/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.bt.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.time.DateUtils;
import org.hibernate.validator.constraints.Range;
import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Non business hours range.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_BUSINESS_HOURS", uniqueConstraints = @UniqueConstraint(columnNames = { "configuration", "start" }))
public class BusinessHours extends AbstractPersistable<Integer>
		implements Comparable<BusinessHours>, Configurable<BugTrackerConfiguration, Integer> {

	/**
	 * Business hours start, inclusive. Unix millisecond, 0 meaning start of
	 * day. 24*60*60*1000 meaning midnight.
	 */
	@Range(min = 0, max = DateUtils.MILLIS_PER_DAY)
	private long start;

	/**
	 * Business hours end, exclusive. Unix millisecond, 0 meaning start of day.
	 * 24*60*60*1000 meaning midnight.
	 */
	@Range(min = 0, max = DateUtils.MILLIS_PER_DAY)
	private long end;

	@ManyToOne
	@NotNull
	@JsonIgnore
	private BugTrackerConfiguration configuration;

	@Override
	public int compareTo(@NotNull final BusinessHours o) {
		return (int) (start - o.start);
	}

}
