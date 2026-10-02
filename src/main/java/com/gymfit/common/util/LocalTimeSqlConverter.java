package com.gymfit.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Time;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Converter
public class LocalTimeSqlConverter
        implements AttributeConverter<LocalTime, Time> {

    @Override
    public Time convertToDatabaseColumn(LocalTime value) {
        if (value == null) {
            return null;
        }

        LocalTime normalized =
                value.truncatedTo(ChronoUnit.SECONDS);

        return Time.valueOf(normalized);
    }

    @Override
    public LocalTime convertToEntityAttribute(Time value) {
        if (value == null) {
            return null;
        }

        return value.toLocalTime();
    }
}