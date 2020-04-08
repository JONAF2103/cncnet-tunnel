import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Observable} from 'rxjs';
import {LoginRequest, LoginResponse} from '../models/login';
import {map} from 'rxjs/operators';
import {environment} from '../../environments/environment';
import {NetworkService} from "./network.service";

@Injectable({
  providedIn: 'root'
})
export class LoginService extends NetworkService {

  private apiKey: string;
  private url = 'login';

  constructor() {
    super();
  }

  login(username: string, password: string): Observable<LoginResponse> {
    const loginRequest: LoginRequest = {
      username,
      password: btoa(password)
    };
    return this.postEntity<LoginResponse>(
      this.url,
      loginRequest
    ).pipe(map(response => {
        this.apiKey = response.apiKey;
        console.log('KEY', this.apiKey);
        return response;
      }));
  }

  loggedIn(): boolean {
    return !!this.apiKey;
  }

  getApiKey(): string {
    return this.apiKey;
  }

  getAuthHeaders(): Headers {
    const headers = new Headers();
    headers.set('api_key', this.apiKey);
    return headers;
  }
}
