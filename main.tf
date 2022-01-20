terraform {
    backend "remote" {
        organization = "vivid-solutions"
        workspaces {
            name = "nr-bcws-opensearch"
        }
    }
}

resource "null_resource" "example" {
}
