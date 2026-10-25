package com.plantshop.user.entity;

/** MARKETING needs MARKETING_EMAIL consent; TRANSACTIONAL mail is part of the contract and is never gated. */
public enum EmailCategory {
    MARKETING,
    TRANSACTIONAL
}
