# Getting started with this example

## 0. About This Example

This example was mainly created to provide a provider needed for the invoice system of the company I work for. My system focuses only on **purchase invoices (incoming invoices)**, which is why the workflow is centered solely on **retrieving** invoices rather than **sending** them.

I hope this example will be helpful to others, which is why I am sharing it publicly.


## 1. Required Properties

For the proper workflow of this examples, you need to create a `src\main\resources\application.properties` file. Inside it, configure the following three parameters used in `KsefAuthorizationProvider` and `KsefApiProps`:

- `ksef.apiToken` – API token. After logging in to your organization, you can obtain an API token from the **Token** tab (See GUI).
- `ksef.nip` – NIP number of the company.
- `ksef.url` – For example: `https://ksef-test.mf.gov.pl/` (for testing purposes).

---

You can also see that the `@PostConstruct` in **Example No. 1** is **commented out**.
Before running it, you should first create `test.xml`, which will provide the invoice **XML** object:
`ksefprocessdemo\src\main\java\pl\pbs\edu\ksefprocessdemo\demo\test.xml`

