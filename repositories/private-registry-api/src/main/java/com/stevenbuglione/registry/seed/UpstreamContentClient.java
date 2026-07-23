package com.stevenbuglione.registry.seed;

import java.net.URI;
import java.nio.file.Path;

interface UpstreamContentClient {

  void downloadTo(URI uri, Path destination);

  byte[] downloadBytes(URI uri);
}
