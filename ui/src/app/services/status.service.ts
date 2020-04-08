import {Injectable, OnDestroy} from '@angular/core';
import {Status} from '../models/status';
import {LoginService} from './login.service';
import {Subject} from 'rxjs';
import {NetworkService} from "./network.service";

@Injectable({
  providedIn: 'root'
})
export class StatusService extends NetworkService implements OnDestroy {

  private intervalPeriod = 50000;
  private interval: any;
  onStatusUpdate: Subject<Status> = new Subject<Status>();
  private url = 'status';

  constructor(private loginService: LoginService) {
    super();
  }

  startUpdating(): void {
    this.interval = setInterval(() => {
      this.retrieveStatus();
    }, this.intervalPeriod);
    this.retrieveStatus();
  }

  private retrieveStatus(): void {
    const subscription = this.getEntity<Status>(
      this.url,
      this.loginService.getAuthHeaders())
      .subscribe(status => {
          this.onStatusUpdate.next(status);
          subscription.unsubscribe();
      });
  }

  ngOnDestroy(): void {
    clearInterval(this.interval);
    this.onStatusUpdate.unsubscribe();
  }
}
