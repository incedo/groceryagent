# Template: CI/CD, hosting en request-routing

Status: `DRAFT | PROPOSED | AGREED | SATISFIED`

Eigenaar: `<team of rol>`

Laatst bijgewerkt: `<YYYY-MM-DD>`

## Doel

Gebruik dit document om voor een project vast te leggen hoe broncode veilig wordt gevalideerd, gebouwd, gepubliceerd en uitgerold, en hoe verkeer door de hostingomgeving loopt. Vervang alle waarden tussen `<...>` en verwijder onderdelen die niet van toepassing zijn.

Dit is een architectuurtemplate. Voeg nooit wachtwoorden, tokens, private sleutels of andere geheime waarden toe.

## Projectvariabelen

| Onderdeel | Waarde |
|---|---|
| Project | `<projectnaam>` |
| Repository | `<organisatie/repository>` |
| Standaardbranch | `<main>` |
| Applicaties/services | `<service-a>, <service-b>` |
| Achtergrondprocessen | `<worker, importer, cronjob>` |
| Containerregistry | `<registry-host>/<project>` |
| Kubernetes-cluster | `<clusternaam>` |
| Kubernetes-namespace | `<namespace>` |
| GitOps-controller | `<Argo CD, Flux of anders>` |
| Publieke of interne hostnaam | `<app.example.internal>` |
| Ingress-controller | `<Traefik, NGINX of anders>` |
| Database | `<PostgreSQL, MySQL of anders>` |
| Object storage | `<S3-compatibel, cloud bucket of n.v.t.>` |
| Secretbeheer | `<External Secrets, SOPS, Vault of anders>` |
| Observability | `<metrics, logs, traces en alerts>` |

## Uitgangspunten

1. **Wijzigingen gaan via een pull request.** De standaardbranch wordt beschermd tegen directe pushes en force-pushes.
2. **Checks zijn risicogestuurd.** Documentatiewijzigingen krijgen lichte controles; code, configuratie, schema's en infrastructuur doorlopen de relevante bouw- en teststappen.
3. **Build once, promote the same artifact.** Het geteste artifact wordt gepubliceerd en uitgerold zonder opnieuw te bouwen.
4. **Artifacts zijn herleidbaar.** Elke release krijgt een onveranderlijke tag of digest die naar exact één commit verwijst.
5. **Deployments zijn declaratief.** De gewenste toestand staat in Git en een GitOps-controller brengt het cluster daarmee in overeenstemming.
6. **Secrets staan buiten Git.** Pipelines en workloads ontvangen alleen referenties naar secrets uit het gekozen secretbeheer.
7. **Least privilege geldt overal.** CI-runners, serviceaccounts, containers, databases en buckets krijgen alleen de minimaal benodigde rechten.
8. **Externe of quota-gevoelige acties zijn expliciet.** Importers, migraties en batchjobs starten niet automatisch tenzij dat bewust is vastgelegd.
9. **Rollback is vooraf ontworpen.** Een vorige immutable image en compatibele configuratie kunnen gecontroleerd worden teruggezet.
10. **Elke stap is observeerbaar.** Build, publicatie, rollout en runtime hebben bruikbare status, logs en alerts.

## Wijzigingsclassificatie en minimale checks

Pas deze matrix aan de repository aan.

| Wijziging | Minimale controle | Bouw artifact | Deploy toegestaan |
|---|---|---:|---:|
| Alleen documentatie | Markdown- en diffcontrole | Nee | Nee |
| Applicatiecode | Unit-, integratie- en contracttests | Ja | Na geslaagde checks |
| Database of event-schema | Migratie-, compatibiliteits- en regressietests | Ja | Na expliciete verificatie |
| Container of buildconfiguratie | Native/containerbuild en smoke test | Ja | Na geslaagde checks |
| Kubernetes of infrastructuur | Schema-, policy- en dry-runvalidatie | Indien nodig | Via GitOps |
| Secrets of credentials | Buiten Git valideren en gecontroleerd roteren | Nee | Alleen met audittrail |

## CI/CD-route

```text
Functionele werkbranch
(bijvoorbeeld feature/*, fix/*, docs/* of chore/*)
        |
        v
Pull request
        |
        v
Wijzigingsclassificatie
        |
        +--> alleen documentatie --> lichte controles
        |
        +--> code/config/infrastructuur
                |
                v
          tests + build + smoke tests
                |
                v
          verplichte checks groen
                |
                v
Merge naar <standaardbranch>
        |
        v
Trusted release-runner
        |
        v
Publiceer immutable artifact
        |
        v
Werk gewenste GitOps-versie bij
        |
        v
GitOps-controller synchroniseert cluster
        |
        v
Readiness, metrics en functionele smoke test
```

### Pull-requestfase

- Draai onbetrouwbare PR-code in een geïsoleerde runner zonder productiesecrets.
- Bouw de artifacts die later gepubliceerd worden, voor zover de vertrouwensgrens dat toelaat.
- Start tijdelijke afhankelijkheden, zoals een database, alleen voor de duur van de test.
- Test minimaal processtart, liveness, readiness en één representatief functioneel pad.
- Publiceer vanuit een fork of onbeoordeelde PR nooit naar een productieregistry.

### Releasefase

- Start alleen na een geslaagde merge of een expliciet goedgekeurde release-tag.
- Gebruik een vertrouwde runner met toegang tot de private registry en deploymentomgeving.
- Publiceer minimaal `<registry>/<project>/<service>:sha-<volledige-commit-sha>`.
- Een beweeglijke tag zoals `main`, `stable` of `latest` mag alleen verwijzen naar hetzelfde geteste artifact.
- Leg de immutable tag of digest vast in de gewenste deploymentconfiguratie.
- Laat de GitOps-controller de wijziging toepassen; wijzig resources niet handmatig als normale deployroute.

## Artifact- en rolloutstrategie

Voorkeursmodel:

```text
commit SHA
   -> getest containerartifact
   -> immutable image-tag of digest
   -> deploymentmanifest
   -> GitOps-sync
   -> rolloutstatus
```

Een beweeglijke tag met `imagePullPolicy: Always` is niet voldoende om een nieuwe rollout te starten: zonder wijziging aan het Pod-template kan Kubernetes de bestaande Pods laten staan. Gebruik daarom bij voorkeur een immutable tag of digest in het manifest.

Leg per service vast:

| Service | Artifact | Immutable referentie | Rollout-trigger | Rollback |
|---|---|---|---|---|
| `<service>` | `<container/binary/package>` | `<sha-tag of digest>` | `<GitOps manifestwijziging>` | `<vorige referentie>` |

## Hostingmodel

```text
Client
  |
  v
DNS
  |
  v
Edge proxy / load balancer
  |
  v
Cluster-ingress
  |
  v
ClusterIP-service
  |
  v
Applicatiepod(s)
  |
  +--> Database
  |
  +--> Object storage
  |
  +--> Event broker of externe provider (optioneel)
```

### Verantwoordelijkheden per laag

| Laag | Verantwoordelijkheid |
|---|---|
| DNS | Naam naar het juiste netwerkendpoint laten wijzen |
| Edge proxy/load balancer | TLS, toegang, routing en eventueel rate limiting |
| Ingress | Host- en padgebaseerde routering naar clusterservices |
| ClusterIP-service | Stabiel intern endpoint en load balancing naar Pods |
| Applicatie | Validatie, autorisatie, domeinlogica en API-contracten |
| Database | Duurzame transactionele data en projecties |
| Object storage | Grote of binaire assets met lifecyclebeleid |
| Eventlaag | Ontkoppelde verwerking, synchronisatie en audittrail |

## Runtime-routes

### API-request

```text
Client request
  -> <app-host>/<api-pad>
  -> DNS
  -> edge proxy
  -> ingressroute
  -> Kubernetes-service
  -> applicatie
  -> database/projectie
  -> API-response
```

Documenteer hierbij:

- welke laag TLS beëindigt;
- waar authenticatie en autorisatie plaatsvinden;
- welke time-outs en maximale payloadgrootten gelden;
- hoe correlation- of request-ID's door de keten reizen;
- welke cache- en freshnessregels gelden;
- welke foutcodes clients mogen verwachten.

### Asset-request

```text
Applicatie of database bewaart assetmetadata
  -> client ontvangt een stabiele asset-URL
  -> asset-host of CDN
  -> object-storagebucket
  -> assetresponse
```

Bewaar in de database bij voorkeur metadata en provenance, niet het binaire bestand zelf. Leg minimaal bron, object key, contenttype, checksum, grootte, ophaaltijd, verificatiestatus en zichtbaarheid vast.

### Achtergrondjob of importer

```text
Expliciete start of gecontroleerd schema
  -> job haalt een begrensde batch op
  -> valideert en normaliseert invoer
  -> publiceert domeinevents
  -> slaat events idempotent op
  -> bouwt projecties bij
  -> rapporteert voortgang en fouten
```

Veiligheidsmaatregelen:

- begrens batchgrootte, paralleliteit en requesttempo;
- gebruik retries met exponential backoff en jitter;
- maak verwerking idempotent en hervatbaar via cursors/checkpoints;
- log fouten per record zonder persoonsgegevens of credentials te lekken;
- zet quota-gevoelige jobs standaard uit tot expliciete activering;
- scheid test-, import- en productiecredentials;
- definieer een dead-letter- of herstelroute.

## Omgevingen en promotie

| Omgeving | Doel | Trigger | Data | Externe toegang |
|---|---|---|---|---|
| `<development>` | Snelle lokale feedback | Handmatig | Fixtures/fakes | Beperkt of gemockt |
| `<test>` | Integratie- en contractvalidatie | Pull request | Tijdelijk of synthetisch | Alleen waar noodzakelijk |
| `<staging>` | Productieachtige verificatie | Na merge of goedkeuring | Geanonimiseerd | Gecontroleerd |
| `<production>` | Live gebruik | Goedgekeurde promotie | Productiedata | Least privilege |

Promotie betekent dat hetzelfde artifact naar de volgende omgeving gaat. Alleen configuratie en secretreferenties mogen per omgeving verschillen.

## Secrets en configuratie

- Commit alleen niet-gevoelige configuratie en secretreferenties.
- Laat workloads secrets via het platform of een externe secretprovider ontvangen.
- Gebruik afzonderlijke credentials per service en omgeving.
- Roteer credentials zonder een nieuwe containerimage te hoeven bouwen.
- Redacteer geheimen, persoonsgegevens en ruwe providerresponses uit logs en CI-artifacts.
- Documenteer eigenaar, rotatieproces en noodintrekking buiten dit openbare document.

## Gezondheid en observability

Definieer minimaal:

| Signaal | Endpoint of bron | Betekenis | Actie bij fout |
|---|---|---|---|
| Liveness | `<pad>` | Proces kan blijven draaien | Pod herstarten |
| Readiness | `<pad>` | Verkeer kan veilig worden ontvangen | Uit load balancing halen |
| Startup | `<pad of n.v.t.>` | Trage initialisatie is voltooid | Binnen grens blijven wachten |
| Metrics | `<metrics endpoint>` | Capaciteit, fouten en latency | Alert volgens SLO |
| Logs | `<logging platform>` | Diagnose met correlation-ID | Retentie en redactie toepassen |
| Traces | `<tracing platform>` | End-to-end requestpad | Samplingbeleid toepassen |

Koppel alerts aan gebruikersimpact en service-level objectives, niet alleen aan infrastructuurmetingen.

## Rollback en herstel

1. Selecteer de laatst bekende werkende immutable artifactreferentie.
2. Controleer of database- en eventschema's achterwaarts compatibel zijn.
3. Werk de declaratieve deploymentconfiguratie terug.
4. Laat GitOps de rollback uitvoeren.
5. Verifieer readiness en een functioneel requestpad.
6. Leg oorzaak, impact en vervolgactie vast.

Voor destructieve of niet-terugdraaibare migraties is een apart herstelplan verplicht.

## Beveiligingsgrenzen

- PR-runner heeft geen productiecredentials en kan niet publiceren of deployen.
- Release-runner mag alleen naar de bedoelde registry en omgeving publiceren.
- Registry is privé tenzij expliciet anders besloten.
- Clusterworkloads draaien als niet-root met een read-only filesystem waar mogelijk.
- NetworkPolicies beperken verkeer tot noodzakelijke bronnen en doelen.
- Database- en bucketrechten zijn per workload gescheiden.
- Publieke endpoints hebben passende authenticatie, rate limiting en auditlogging.

## Acceptatiechecklist

- [ ] Alle placeholders zijn ingevuld of bewust verwijderd.
- [ ] Branchbescherming en verplichte checks zijn actief.
- [ ] Documentatiewijzigingen worden niet onnodig als applicatie gebouwd.
- [ ] Codewijzigingen doorlopen tests, build en smoke tests.
- [ ] Het gepubliceerde artifact is exact het geteste artifact.
- [ ] Elke release heeft een immutable tag of digest.
- [ ] De rollout wordt door een declaratieve wijziging getriggerd.
- [ ] Secrets en persoonsgegevens staan niet in Git, logs of artifacts.
- [ ] Readiness, logging, metrics en alerts zijn ingericht.
- [ ] Rollback is getest en gedocumenteerd.
- [ ] Batchjobs zijn begrensd, idempotent en standaard veilig geconfigureerd.
- [ ] De volledige netwerkroute is vanaf een client geverifieerd.

## Open beslissingen

| Beslissing | Opties | Keuze | Eigenaar | Deadline |
|---|---|---|---|---|
| `<bijvoorbeeld rollout-trigger>` | `<optie A/B>` | `<nog te bepalen>` | `<naam/rol>` | `<datum>` |

## Voorbeeld: Grocery

Onderstaand voorbeeld laat alleen zien hoe de template kan worden ingevuld; het bevat geen omgevingsspecifieke details.

| Onderdeel | Voorbeeldwaarde |
|---|---|
| Project | `Grocery` |
| Applicaties/services | `catalog API` |
| Achtergrondprocessen | `productimporter` en `afbeeldingensynchronisatie` |
| Database | `PostgreSQL` voor producten, aanbiedingen, prijshistorie en events |
| Object storage | `S3-compatibele opslag` voor productafbeeldingen |
| Runtime-route | `client -> ingress -> catalog API -> productprojectie` |
| Import-route | `begrensde batch -> provideradapter -> canoniek productevent -> databaseprojectie` |
| Rollout | `immutable image per commit, uitgerold via GitOps` |

Bij een echt project worden hostnamen, IP-adressen, registrylocaties, bucketnamen, accountgegevens, concrete endpoints en secret-namen alleen in de daarvoor bedoelde configuratie of het secretbeheer vastgelegd.
