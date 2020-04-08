import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {Configuration} from '../models/configuration';
import {LoginService} from './login.service';
import {map} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {NetworkService} from "./network.service";

@Injectable({
  providedIn: 'root'
})
export class ConfigurationService extends NetworkService {

  private url = 'configuration';

  constructor(private loginService: LoginService) {
    super()
  }

  getConfiguration(): Observable<Configuration> {
    return this.getEntity<Configuration>(
      this.url,
      this.loginService.getAuthHeaders()
    );
  }

  updateConfiguration(configuration: Configuration): Observable<boolean> {
    return this.post(
      this.url,
      configuration,
      this.loginService.getAuthHeaders())
      .pipe(map(response => response.status === 200));
  }

  startTunnel(): Observable<boolean> {
    return this.put(
      this.url,
      null,
      this.loginService.getAuthHeaders())
      .pipe(map(response => response.status === 200));
  }
}
