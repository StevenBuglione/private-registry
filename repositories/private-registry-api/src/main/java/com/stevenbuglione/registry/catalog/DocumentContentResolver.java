package com.stevenbuglione.registry.catalog;

public interface DocumentContentResolver {

  String readVerified(String key, String expectedSha256Digest);
}
