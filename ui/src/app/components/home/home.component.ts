import {Component, OnInit} from '@angular/core';
import {Configuration} from '../../models/configuration';
import {ConfigurationService} from '../../services/configuration.service';
import {StatusService} from '../../services/status.service';
import {Status} from '../../models/status';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  loading = true;

  title = 'CNC Tunnel - Home';

  configuration: Configuration;
  status: Status;

  constructor(private configurationService: ConfigurationService,
              private statusService: StatusService) { }

  ngOnInit(): void {
    this.statusService.onStatusUpdate.subscribe(status => {
      if (status.serverLog && status.serverLog.length > 0) {
        status.serverLog = status.serverLog.map(line => line + '\n');
      }
      this.status = status;
    });
    this.statusService.startUpdating();
    this.configurationService.getConfiguration()
      .subscribe(configuration => {
          this.configuration = configuration;
          this.loading = false;
      });
  }

  updateConfiguration(): void {
    this.loading = true;
    this.configurationService.updateConfiguration(this.configuration).subscribe(updated => {
      if (updated) {
        if (this.configuration.tunnelEnabled) {
          this.configurationService.startTunnel().subscribe(started => {
            if (started) {
              console.log('Tunnel started');
              this.configurationService.getConfiguration().subscribe(configuration => {
                this.configuration = configuration;
                this.loading = false;
              })
            }
          })
        } else {
          this.loading = false;

        }
      }
    })
  }

}
