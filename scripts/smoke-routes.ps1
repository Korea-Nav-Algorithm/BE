param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [Parameter(Mandatory = $true)][double]$OriginLat,
    [Parameter(Mandatory = $true)][double]$OriginLng,
    [Parameter(Mandatory = $true)][double]$WaypointLat,
    [Parameter(Mandatory = $true)][double]$WaypointLng,
    [Parameter(Mandatory = $true)][double]$DestinationLat,
    [Parameter(Mandatory = $true)][double]$DestinationLng,
    [int]$MaxElapsedMs = 5000
)

$ErrorActionPreference = 'Stop'
$legs = @(
    @{ name = 'origin-to-waypoint'; origin = @{ lat = $OriginLat; lng = $OriginLng }; destination = @{ lat = $WaypointLat; lng = $WaypointLng } },
    @{ name = 'waypoint-to-destination'; origin = @{ lat = $WaypointLat; lng = $WaypointLng }; destination = @{ lat = $DestinationLat; lng = $DestinationLng } }
)

foreach ($leg in $legs) {
    foreach ($algorithm in @('BASELINE', 'DIRECTION_AWARE')) {
        $body = @{ origin = $leg.origin; destination = $leg.destination; algorithm = $algorithm } |
            ConvertTo-Json -Compress
        $timer = [System.Diagnostics.Stopwatch]::StartNew()
        $route = Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/api/routes" -Method Post `
            -ContentType 'application/json' -Body $body
        $timer.Stop()

        if ([string]::IsNullOrWhiteSpace($route.routeId) -or $route.algorithm -ne $algorithm -or
            $route.distanceMeters -le 0 -or $route.durationSeconds -le 0 -or
            @($route.geometry).Count -lt 2 -or @($route.segments).Count -lt 1) {
            throw "Invalid route for $($leg.name) / $algorithm"
        }
        if ($timer.ElapsedMilliseconds -gt $MaxElapsedMs) {
            throw "Route exceeded $MaxElapsedMs ms for $($leg.name) / $algorithm: $($timer.ElapsedMilliseconds) ms"
        }
        [pscustomobject]@{
            leg = $leg.name
            algorithm = $algorithm
            routeId = $route.routeId
            algorithmVersion = $route.algorithmVersion
            distanceMeters = $route.distanceMeters
            durationSeconds = $route.durationSeconds
            geometryCount = @($route.geometry).Count
            segmentCount = @($route.segments).Count
            elapsedMs = $timer.ElapsedMilliseconds
        }
    }
}
