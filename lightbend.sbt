resolvers in ThisBuild += "lightbend-commercial-mvn" at
  "https://repo.lightbend.com/pass/lE7ZNKXvMoB-Z2YANZs3JxJpq2wbgc15mauX9KTd90HDiEHW/commercial-releases"
resolvers in ThisBuild += Resolver.url("lightbend-commercial-ivy",
  url("https://repo.lightbend.com/pass/lE7ZNKXvMoB-Z2YANZs3JxJpq2wbgc15mauX9KTd90HDiEHW/commercial-releases"))(Resolver.ivyStylePatterns)