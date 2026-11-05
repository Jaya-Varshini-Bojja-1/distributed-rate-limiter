/**
 * Token Bucket algorithm and Redis Lua client.
 * Production refill math lives only in {@code redis/token_bucket.lua}.
 * This module stays free of HTTP, MVC, and JPA.
 */
package com.ratelimiter.core;
